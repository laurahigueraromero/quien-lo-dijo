package com.quienlodijo.backend.round;

import com.quienlodijo.backend.domain.Answer;
import com.quienlodijo.backend.domain.Question;
import com.quienlodijo.backend.domain.Room;
import com.quienlodijo.backend.domain.RoomPlayer;
import com.quienlodijo.backend.domain.Round;
import com.quienlodijo.backend.domain.RoundStatus;
import com.quienlodijo.backend.domain.User;
import com.quienlodijo.backend.repository.AnswerRepository;
import com.quienlodijo.backend.repository.QuestionRepository;
import com.quienlodijo.backend.repository.RoomPlayerRepository;
import com.quienlodijo.backend.repository.RoundRepository;
import com.quienlodijo.backend.repository.UserRepository;
import com.quienlodijo.backend.room.RoomService;
import com.quienlodijo.backend.room.dto.RoomEvent;
import com.quienlodijo.backend.round.dto.RoundAnsweringStartedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor de rondas: crea la siguiente {@link Round} en fase ANSWERING, gestiona el
 * temporizador de 60s y recibe las respuestas de los jugadores (spec.md US-4,
 * plan.md §3/§6, T016-T018).
 *
 * <p>Alcance de la Fase 4: llega hasta cerrar la fase ANSWERING (por timeout o porque
 * todos los jugadores activos ya respondieron). El sorteo de una respuesta y el paso a
 * BETTING son de la Fase 5 (T021) — quedan marcados con TODO en {@link #closeAnsweringPhase}.
 */
@Service
public class GameEngineService {

    /** Duración de la fase de respuesta (spec.md §6, valor por defecto fijo del MVP). */
    public static final int ANSWERING_SECONDS = 60;

    private final RoomService roomService;
    private final QuestionRepository questionRepository;
    private final RoundRepository roundRepository;
    private final AnswerRepository answerRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;

    /** Timers de respuesta activos por id de ronda, para poder cancelarlos si se cierra antes. */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> answeringTimers = new ConcurrentHashMap<>();

    /**
     * Ids de ronda cuya fase ANSWERING ya se ha cerrado. Actúa como guarda atómica: si el
     * timeout y la última respuesta llegan casi a la vez, solo uno de los dos consigue
     * insertar el id (Set.add es atómico en ConcurrentHashMap.newKeySet()), evitando cerrar
     * la fase dos veces (T018, condición de carrera).
     */
    private final Set<Long> answeringClosed = ConcurrentHashMap.newKeySet();

    public GameEngineService(
            RoomService roomService,
            QuestionRepository questionRepository,
            RoundRepository roundRepository,
            AnswerRepository answerRepository,
            RoomPlayerRepository roomPlayerRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            TaskScheduler taskScheduler) {
        this.roomService = roomService;
        this.questionRepository = questionRepository;
        this.roundRepository = roundRepository;
        this.answerRepository = answerRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
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

        closeAnsweringPhaseIfEveryoneAnswered(round);
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

    private void closeAnsweringPhaseIfEveryoneAnswered(Round round) {
        long activePlayers =
                roomPlayerRepository.findByRoom(round.getRoom()).stream().filter(p -> !p.isEliminated()).count();
        long answered = answerRepository.findByRound(round).size();
        if (answered >= activePlayers) {
            closeAnsweringPhase(round.getId());
        }
    }

    /**
     * Cierra la fase ANSWERING de una ronda, ya sea por timeout o porque todos respondieron.
     * Idempotente gracias a {@link #answeringClosed} (T018).
     */
    private void closeAnsweringPhase(Long roundId) {
        if (!answeringClosed.add(roundId)) {
            return; // ya se había cerrado (timeout y última respuesta casi simultáneos)
        }
        cancelAnsweringTimer(roundId);
        // TODO (Fase 5, T021): sortear una respuesta con contenido y pasar la ronda a BETTING.
        // Nota de implementación para quien lo añada: este método se invoca también desde un
        // callback del TaskScheduler (hilo distinto al de la petición), así que la lógica que
        // toque la base de datos aquí no puede depender de @Transactional en un método privado
        // de esta misma clase (la auto-invocación no pasa por el proxy de Spring); debe ir en
        // un método público, o publicarse como evento y manejarse en otro bean.
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

    private void broadcastAnsweringStarted(Round round) {
        var payload =
                new RoundAnsweringStartedEvent(
                        round.getId(), round.getQuestion().getText(), round.getAnsweringEndsAt().toString());
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + round.getRoom().getCode(), new RoomEvent("ROUND_ANSWERING_STARTED", payload));
    }
}
