package com.quienlodijo.backend.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.quienlodijo.backend.domain.Bet;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;

/**
 * Test unitario de la fórmula económica de resolución de ronda (spec.md §6, T026):
 * los tres casos — nadie acierta, todos aciertan, mezcla — con los saldos exactos
 * esperados, más la eliminación de un jugador que se queda a 0 (T027).
 *
 * <p>El flujo completo (WebSocket, timers, concurrencia) ya está cubierto por las pruebas
 * de extremo a extremo manuales de las Fases 4-5; este test se centra solo en la
 * aritmética de {@link GameEngineService#resolveRound}, con las dependencias mockeadas.
 */
@ExtendWith(MockitoExtension.class)
class GameEngineServiceResolveRoundTest {

    @Mock private RoomService roomService;
    @Mock private QuestionRepository questionRepository;
    @Mock private RoundRepository roundRepository;
    @Mock private AnswerRepository answerRepository;
    @Mock private BetRepository betRepository;
    @Mock private RoomPlayerRepository roomPlayerRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private TaskScheduler taskScheduler;

    private GameEngineService gameEngineService;
    private Room room;
    private RoomPlayer author;

    @BeforeEach
    void setUp() {
        gameEngineService =
                new GameEngineService(
                        roomService,
                        questionRepository,
                        roundRepository,
                        answerRepository,
                        betRepository,
                        roomPlayerRepository,
                        userRepository,
                        messagingTemplate,
                        taskScheduler,
                        null); // self solo lo usan las rutas de cierre por timeout/completitud, no resolveRound

        room = Room.builder().id(1L).code("TESTRM").build();
        author = player(1L, 50);

        // resolveRound encadena startNextRound al terminar; sin preguntas jugables, corta enseguida.
        when(questionRepository.findByRoomAndDiscardedFalseOrderByPlayOrderAsc(any())).thenReturn(List.of());
        when(roundRepository.findByRoom(any())).thenReturn(List.of());
    }

    private RoomPlayer player(long id, int saldoFichas) {
        User user = User.builder().id(id).username("user" + id).build();
        return RoomPlayer.builder().id(id).room(room).user(user).saldoFichas(saldoFichas).eliminated(false).build();
    }

    private Round roundWithAuthor() {
        return Round.builder().id(100L).room(room).authorPlayer(author).status(RoundStatus.BETTING).build();
    }

    private Bet bet(RoomPlayer bettor, RoomPlayer candidate, int amount) {
        return Bet.builder().round(roundWithAuthor()).bettor(bettor).candidate(candidate).amount(amount).build();
    }

    @Test
    void nadieAcierta_elAutorSeLlevaElBoteCompleto() {
        RoomPlayer bettor1 = player(2L, 40); // ya ha apostado 10 de un saldo inicial de 50
        RoomPlayer bettor2 = player(3L, 45); // ya ha apostado 5 de un saldo inicial de 50
        Bet bet1 = bet(bettor1, bettor2, 10); // falla: apuesta por bettor2, el autor es "author"
        Bet bet2 = bet(bettor2, bettor1, 5); // falla: apuesta por bettor1

        Round round = roundWithAuthor();
        when(betRepository.findByRound(round)).thenReturn(List.of(bet1, bet2));
        when(roomPlayerRepository.findByRoom(room)).thenReturn(List.of(author, bettor1, bettor2));

        gameEngineService.resolveRound(round);

        assertThat(author.getSaldoFichas()).isEqualTo(65); // 50 + bote de fallos (10+5)
        assertThat(bettor1.getSaldoFichas()).isEqualTo(40); // sin cambios, ya perdió su apuesta
        assertThat(bettor2.getSaldoFichas()).isEqualTo(45);
        assertThat(bet1.getCorrect()).isFalse();
        assertThat(bet2.getCorrect()).isFalse();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.RESOLVED);
    }

    @Test
    void todosAciertan_cadaUnoRecuperaSuApuestaSinGananciaExtra() {
        RoomPlayer bettor1 = player(2L, 40); // apostó 10
        RoomPlayer bettor2 = player(3L, 45); // apostó 5
        Bet bet1 = bet(bettor1, author, 10); // acierta
        Bet bet2 = bet(bettor2, author, 5); // acierta

        Round round = roundWithAuthor();
        when(betRepository.findByRound(round)).thenReturn(List.of(bet1, bet2));
        when(roomPlayerRepository.findByRoom(room)).thenReturn(List.of(author, bettor1, bettor2));

        gameEngineService.resolveRound(round);

        assertThat(bettor1.getSaldoFichas()).isEqualTo(50); // recupera su apuesta, sin más
        assertThat(bettor2.getSaldoFichas()).isEqualTo(50);
        assertThat(author.getSaldoFichas()).isEqualTo(50); // sin bonus, no hubo fallos
        assertThat(bet1.getCorrect()).isTrue();
        assertThat(bet2.getCorrect()).isTrue();
    }

    @Test
    void mezclaDeAciertosYFallos_reparteElBoteYBonificaAlAutor() {
        RoomPlayer bettor1 = player(2L, 40); // apostó 10, acierta
        RoomPlayer bettor2 = player(3L, 45); // apostó 5, falla
        Bet bet1 = bet(bettor1, author, 10); // acierta
        Bet bet2 = bet(bettor2, bettor1, 5); // falla

        Round round = roundWithAuthor();
        when(betRepository.findByRound(round)).thenReturn(List.of(bet1, bet2));
        when(roomPlayerRepository.findByRoom(room)).thenReturn(List.of(author, bettor1, bettor2));

        gameEngineService.resolveRound(round);

        // bettor1: recupera su apuesta (10) + el bote de fallos completo (5, único acertante) = +15
        assertThat(bettor1.getSaldoFichas()).isEqualTo(55);
        assertThat(bettor2.getSaldoFichas()).isEqualTo(45); // sin cambios, ya perdió
        assertThat(author.getSaldoFichas()).isEqualTo(52); // 50 + 2 fichas por el único fallo
        assertThat(bet1.getCorrect()).isTrue();
        assertThat(bet2.getCorrect()).isFalse();
    }

    @Test
    void jugadorQueLlegaA0Fichas_quedaEliminado() {
        RoomPlayer bettor = player(2L, 0); // apostó todo lo que tenía (ya descontado) y falla
        RoomPlayer otroJugador = player(3L, 10);
        Bet betQueFalla = bet(bettor, otroJugador, 5); // candidato incorrecto: no es el autor

        Round round = roundWithAuthor();
        when(betRepository.findByRound(round)).thenReturn(List.of(betQueFalla));
        when(roomPlayerRepository.findByRoom(room)).thenReturn(List.of(author, bettor));

        gameEngineService.resolveRound(round);

        assertThat(bettor.getSaldoFichas()).isEqualTo(0);
        assertThat(bettor.isEliminated()).isTrue();
    }
}
