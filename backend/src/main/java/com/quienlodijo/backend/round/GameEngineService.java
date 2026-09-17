package com.quienlodijo.backend.round;

import com.quienlodijo.backend.domain.Answer;
import com.quienlodijo.backend.domain.Bet;
import com.quienlodijo.backend.domain.Question;
import com.quienlodijo.backend.domain.Room;
import com.quienlodijo.backend.domain.RoomPlayer;
import com.quienlodijo.backend.domain.Round;
import com.quienlodijo.backend.domain.RoundStatus;
import com.quienlodijo.backend.domain.User;
import com.quienlodijo.backend.repository.AnswerRepository;
import com.quienlodijo.backend.repository.BetRepository;
import com.quienlodijo.backend.repository.QuestionRepository;
import com.quienlodijo.backend.repository.RoomPlayerRepository;
import com.quienlodijo.backend.repository.RoundRepository;
import com.quienlodijo.backend.repository.UserRepository;
import com.quienlodijo.backend.room.RoomService;
import com.quienlodijo.backend.room.dto.RoomEvent;
import com.quienlodijo.backend.round.dto.RoundAnsweringStartedEvent;
import com.quienlodijo.backend.round.dto.RoundBettingStartedEvent;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor de rondas: pregunta -> respuestas -> apuesta -> (resolución en Fase 6)
 * (spec.md US-4/US-5, plan.md §3/§6, T016-T018, T021-T023).
 *
 * <p>Alcance de la Fase 5: llega hasta cerrar la fase BETTING (por timeout o porque todos
 * los jugadores que debían apostar ya lo hicieron, con apuesta automática para quien no
 * llegó a tiempo). El reparto económico de fichas es la Fase 6 (T026) — queda marcado con
 * TODO en {@link #performCloseBettingPhase}.
 */
@Service
public class GameEngineService {

    /** Duración de la fase de respuesta (spec.md §6, valor por defecto fijo del MVP). */
    public static final int ANSWERING_SECONDS = 60;

    /** Duración de la fase de apuesta (spec.md §6, valor por defecto fijo del MVP). */
    public static final int BETTING_SECONDS = 30;

    private final RoomService roomService;
    private final QuestionRepository questionRepository;
    private final RoundRepository roundRepository;
    private final AnswerRepository answerRepository;
    private final BetRepository betRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;
    private final SecureRandom random = new SecureRandom();

    /**
     * Referencia a sí mismo obtenida vía el proxy de Spring (por eso @Lazy: evita el ciclo al
     * construir el bean). Los timeouts programados con TaskScheduler corren en un hilo sin
     * transacción activa; si esos callbacks llamaran a un método @Transactional con `this.`,
     * la auto-invocación se saltaría el proxy de Spring y la anotación no tendría efecto. Se
     * llama a través de `self` para que sí se abra una transacción real en ese caso.
     */
    private final GameEngineService self;

    /** Timers activos por id de ronda (respuesta o apuesta), para poder cancelarlos si se cierra antes. */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> answeringTimers = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, ScheduledFuture<?>> bettingTimers = new ConcurrentHashMap<>();

    /**
     * Ids de ronda cuya fase ANSWERING/BETTING ya se ha cerrado. Actúa como guarda atómica: si
     * el timeout y la última acción (responder/apostar) llegan casi a la vez, solo uno de los
     * dos consigue insertar el id (Set.add es atómico en ConcurrentHashMap.newKeySet()),
     * evitando cerrar la fase dos veces (T018/T023, condición de carrera).
     */
    private final Set<Long> answeringClosed = ConcurrentHashMap.newKeySet();

    private final Set<Long> bettingClosed = ConcurrentHashMap.newKeySet();

    public GameEngineService(
            RoomService roomService,
            QuestionRepository questionRepository,
            RoundRepository roundRepository,
            AnswerRepository answerRepository,
            BetRepository betRepository,
            RoomPlayerRepository roomPlayerRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            TaskScheduler taskScheduler,
            @Lazy GameEngineService self) {
        this.roomService = roomService;
        this.questionRepository = questionRepository;
        this.roundRepository = roundRepository;
        this.answerRepository = answerRepository;
        this.betRepository = betRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
        this.self = self;
    }

    /**
     * Crea la siguiente ronda de la sala (la primera pregunta no descartada que todavía no
     * tiene Round) y arranca su temporizador de respuesta. Se apoya en
     * "nº de Rounds ya creadas" como índice sobre la lista de preguntas jugables vigente, lo
     * que se autocorrige si alguna pregunta se descarta más adelante (spec.md §6/§9).
     */
    @Transactional
    public void startNextRound(Room room) {
        List<Question> playable = questionRepository.findByRoomAndDiscardedFalseOrderByPlayOrderAsc(room);
        int roundsCreated = roundRepository.findByRoom(room).size();
        if (roundsCreated >= playable.size()) {
            // TODO (Fase 7, T031): no quedan preguntas -> finalizar la partida.
            return;
        }

        Question next = playable.get(roundsCreated);
        Instant endsAt = Instant.now().plusSeconds(ANSWERING_SECONDS);
        Round round =
                Round.builder().room(room).question(next).status(RoundStatus.ANSWERING).answeringEndsAt(endsAt).build();
        round = roundRepository.save(round);

        broadcastAnsweringStarted(round);
        scheduleAnsweringTimeout(round.getId());
    }

    /** Guarda la respuesta de un jugador a la ronda actualmente activa de la sala (T017). */
    @Transactional
    public void submitAnswer(String code, Long userId, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("La respuesta no puede estar vacía");
        }

        Room room = roomService.getRoomOrThrow(code);
        Round round = getActiveAnsweringRoundOrThrow(room);

        User user =
                userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));
        RoomPlayer player =
                roomPlayerRepository
                        .findByRoomAndUser(room, user)
                        .orElseThrow(() -> new IllegalStateException("No perteneces a esta sala"));

        if (answerRepository.existsByRoundAndPlayer(round, player)) {
            throw new IllegalStateException("Ya has respondido a esta ronda");
        }

        try {
            answerRepository.save(
                    Answer.builder().round(round).player(player).text(text.trim()).submittedAt(Instant.now()).build());
        } catch (DataIntegrityViolationException e) {
            // Dos respuestas casi simultáneas del mismo jugador pueden saltarse el existsBy de
            // arriba (no es atómico con el insert); la restricción única de BD sigue
            // protegiendo la integridad de los datos, aquí solo traducimos el error a uno
            // legible en vez de dejar pasar la excepción cruda de SQL.
            throw new IllegalStateException("Ya has respondido a esta ronda");
        }

        checkAnsweringCompletion(round.getId());
        // Red de seguridad: dos respuestas casi simultáneas corren en transacciones separadas,
        // así que la comprobación de arriba puede no ver todavía el commit de la otra (bajo
        // READ_COMMITTED, cada transacción ve el estado en el momento de su propia consulta,
        // no necesariamente el de una transacción hermana que aún no ha terminado). Si eso pasa,
        // esta segunda comprobación (en una consulta nueva, ya con todos los commits aplicados)
        // cierra la fase en ~300ms en vez de esperar el timeout completo de 60s. Verificado con
        // 3 clientes STOMP enviando su respuesta prácticamente a la vez.
        taskScheduler.schedule(() -> checkAnsweringCompletion(round.getId()), Instant.now().plusMillis(300));
    }

    /**
     * Guarda la apuesta de un jugador sobre quién cree que es el autor de la ronda actual
     * (spec.md US-5, T022). El autor real no puede apostar en su propia ronda.
     */
    @Transactional
    public void submitBet(String code, Long userId, Long candidateUserId, int amount) {
        Room room = roomService.getRoomOrThrow(code);
        Round round = getActiveBettingRoundOrThrow(room);

        User user =
                userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));
        RoomPlayer bettor =
                roomPlayerRepository
                        .findByRoomAndUser(room, user)
                        .orElseThrow(() -> new IllegalStateException("No perteneces a esta sala"));

        if (bettor.isEliminated()) {
            throw new IllegalStateException("Estás eliminado, no puedes apostar");
        }
        if (bettor.getId().equals(round.getAuthorPlayer().getId())) {
            throw new IllegalStateException("No puedes apostar en tu propia ronda");
        }
        if (amount < Bet.APUESTA_MINIMA || amount > bettor.getSaldoFichas()) {
            throw new IllegalArgumentException(
                    "La apuesta debe ser entre " + Bet.APUESTA_MINIMA + " y tu saldo actual (" + bettor.getSaldoFichas() + ")");
        }
        if (betRepository.existsByRoundAndBettor(round, bettor)) {
            throw new IllegalStateException("Ya has apostado en esta ronda");
        }

        User candidateUser =
                userRepository
                        .findById(candidateUserId)
                        .orElseThrow(() -> new IllegalArgumentException("Candidato inválido"));
        RoomPlayer candidate =
                roomPlayerRepository
                        .findByRoomAndUser(room, candidateUser)
                        .orElseThrow(() -> new IllegalArgumentException("Candidato inválido"));

        try {
            betRepository.save(
                    Bet.builder().round(round).bettor(bettor).candidate(candidate).amount(amount).autoAssigned(false).build());
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Ya has apostado en esta ronda");
        }

        bettor.setSaldoFichas(bettor.getSaldoFichas() - amount);
        roomPlayerRepository.save(bettor);

        checkBettingCompletion(round.getId());
        // Misma red de seguridad que en submitAnswer, para apuestas casi simultáneas.
        taskScheduler.schedule(() -> checkBettingCompletion(round.getId()), Instant.now().plusMillis(300));
    }

    private Round getActiveAnsweringRoundOrThrow(Room room) {
        Round round =
                roundRepository
                        .findTopByRoomOrderByIdDesc(room)
                        .orElseThrow(() -> new IllegalStateException("No hay ninguna ronda activa en esta sala"));
        if (round.getStatus() != RoundStatus.ANSWERING || answeringClosed.contains(round.getId())) {
            throw new IllegalStateException("La fase de respuesta de esta ronda ya ha terminado");
        }
        return round;
    }

    private Round getActiveBettingRoundOrThrow(Room room) {
        Round round =
                roundRepository
                        .findTopByRoomOrderByIdDesc(room)
                        .orElseThrow(() -> new IllegalStateException("No hay ninguna ronda activa en esta sala"));
        if (round.getStatus() != RoundStatus.BETTING || bettingClosed.contains(round.getId())) {
            throw new IllegalStateException("La fase de apuestas de esta ronda ya ha terminado");
        }
        return round;
    }

    /**
     * Comprueba si ya han respondido todos los jugadores activos y, si es así, cierra la fase.
     * Se llama tanto de forma inmediata (tras cada respuesta) como con un pequeño retraso
     * (T017/T018 — ver el comentario en submitAnswer sobre la carrera entre transacciones).
     */
    private void checkAnsweringCompletion(Long roundId) {
        if (answeringClosed.contains(roundId)) {
            return;
        }
        Round round = roundRepository.findById(roundId).orElse(null);
        if (round == null || round.getStatus() != RoundStatus.ANSWERING) {
            return;
        }
        long activePlayers =
                roomPlayerRepository.findByRoom(round.getRoom()).stream().filter(p -> !p.isEliminated()).count();
        if (answerRepository.findByRound(round).size() >= activePlayers) {
            closeAnsweringPhase(roundId);
        }
    }

    /**
     * Comprueba si ya han apostado todos los jugadores que debían hacerlo (activos, sin contar
     * al autor) y, si es así, cierra la fase (T022/T023, misma lógica que {@link #checkAnsweringCompletion}).
     */
    private void checkBettingCompletion(Long roundId) {
        if (bettingClosed.contains(roundId)) {
            return;
        }
        Round round = roundRepository.findById(roundId).orElse(null);
        if (round == null || round.getStatus() != RoundStatus.BETTING) {
            return;
        }
        long expectedBettors =
                roomPlayerRepository.findByRoom(round.getRoom()).stream()
                        .filter(p -> !p.isEliminated() && !p.getId().equals(round.getAuthorPlayer().getId()))
                        .count();
        if (betRepository.findByRound(round).size() >= expectedBettors) {
            closeBettingPhase(roundId);
        }
    }

    /** Cierra la fase ANSWERING; idempotente gracias a {@link #answeringClosed} (T018). */
    private void closeAnsweringPhase(Long roundId) {
        if (!answeringClosed.add(roundId)) {
            return; // ya se había cerrado (timeout y última respuesta casi simultáneos)
        }
        cancelAnsweringTimer(roundId);
        self.performCloseAnsweringPhase(roundId);
    }

    /**
     * Sortea una respuesta con contenido entre las recibidas y pasa la ronda a BETTING (T021).
     * Si nadie respondió, la ronda se resuelve sin ganador ni perdedor (spec.md §3/plan.md §3)
     * y se pasa directamente a la siguiente pregunta.
     */
    @Transactional
    public void performCloseAnsweringPhase(Long roundId) {
        Round round = roundRepository.findById(roundId).orElseThrow();
        List<Answer> validAnswers = answerRepository.findByRoundAndTextIsNotNull(round);

        if (validAnswers.isEmpty()) {
            round.setStatus(RoundStatus.RESOLVED);
            round.setResolvedAt(Instant.now());
            roundRepository.save(round);
            startNextRound(round.getRoom());
            return;
        }

        Answer selected = validAnswers.get(random.nextInt(validAnswers.size()));
        Instant bettingEndsAt = Instant.now().plusSeconds(BETTING_SECONDS);

        round.setSelectedAnswer(selected);
        round.setAuthorPlayer(selected.getPlayer());
        round.setStatus(RoundStatus.BETTING);
        round.setBettingEndsAt(bettingEndsAt);
        round = roundRepository.save(round);

        broadcastBettingStarted(round);
        scheduleBettingTimeout(round.getId());
    }

    /** Cierra la fase BETTING; idempotente gracias a {@link #bettingClosed} (T023). */
    private void closeBettingPhase(Long roundId) {
        if (!bettingClosed.add(roundId)) {
            return; // ya se había cerrado (timeout y última apuesta casi simultáneos)
        }
        cancelBettingTimer(roundId);
        self.performCloseBettingPhase(roundId);
    }

    /**
     * A quien no llegó a apostar a tiempo se le asigna automáticamente la apuesta mínima sobre
     * un candidato aleatorio (spec.md US-5, T023). El reparto económico de la ronda (T026) es
     * responsabilidad de la Fase 6.
     */
    @Transactional
    public void performCloseBettingPhase(Long roundId) {
        Round round = roundRepository.findById(roundId).orElseThrow();
        List<RoomPlayer> allPlayers = roomPlayerRepository.findByRoom(round.getRoom());
        RoomPlayer author = round.getAuthorPlayer();

        List<RoomPlayer> possibleCandidates =
                allPlayers.stream().filter(p -> !p.getId().equals(author.getId())).toList();

        List<RoomPlayer> pendingBettors =
                allPlayers.stream()
                        .filter(p -> !p.isEliminated())
                        .filter(p -> !p.getId().equals(author.getId()))
                        .filter(p -> !betRepository.existsByRoundAndBettor(round, p))
                        .toList();

        for (RoomPlayer pending : pendingBettors) {
            List<RoomPlayer> options =
                    possibleCandidates.stream().filter(c -> !c.getId().equals(pending.getId())).toList();
            if (options.isEmpty()) {
                options = possibleCandidates; // caso borde: solo quedaba él mismo como candidato posible
            }
            RoomPlayer candidate = options.get(random.nextInt(options.size()));

            betRepository.save(
                    Bet.builder()
                            .round(round)
                            .bettor(pending)
                            .candidate(candidate)
                            .amount(Bet.APUESTA_MINIMA)
                            .autoAssigned(true)
                            .build());
            pending.setSaldoFichas(pending.getSaldoFichas() - Bet.APUESTA_MINIMA);
            roomPlayerRepository.save(pending);
        }

        // TODO (Fase 6, T026): resolver la economía de la ronda (reparto del bote, bonus del
        // autor, eliminación de jugadores sin saldo) y encadenar la siguiente ronda o el fin
        // de partida.
    }

    private void scheduleAnsweringTimeout(Long roundId) {
        ScheduledFuture<?> future =
                taskScheduler.schedule(() -> closeAnsweringPhase(roundId), Instant.now().plusSeconds(ANSWERING_SECONDS));
        answeringTimers.put(roundId, future);
    }

    private void cancelAnsweringTimer(Long roundId) {
        ScheduledFuture<?> future = answeringTimers.remove(roundId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void scheduleBettingTimeout(Long roundId) {
        ScheduledFuture<?> future =
                taskScheduler.schedule(() -> closeBettingPhase(roundId), Instant.now().plusSeconds(BETTING_SECONDS));
        bettingTimers.put(roundId, future);
    }

    private void cancelBettingTimer(Long roundId) {
        ScheduledFuture<?> future = bettingTimers.remove(roundId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void broadcastAnsweringStarted(Round round) {
        var payload =
                new RoundAnsweringStartedEvent(
                        round.getId(), round.getQuestion().getText(), round.getAnsweringEndsAt().toString());
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + round.getRoom().getCode(), new RoomEvent("ROUND_ANSWERING_STARTED", payload));
    }

    private void broadcastBettingStarted(Round round) {
        List<Long> candidateUserIds =
                roomPlayerRepository.findByRoom(round.getRoom()).stream()
                        .filter(p -> !p.getId().equals(round.getAuthorPlayer().getId()))
                        .map(p -> p.getUser().getId())
                        .toList();
        var payload =
                new RoundBettingStartedEvent(
                        round.getId(),
                        round.getSelectedAnswer().getText(),
                        candidateUserIds,
                        round.getBettingEndsAt().toString());
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + round.getRoom().getCode(), new RoomEvent("ROUND_BETTING_STARTED", payload));
    }
}
