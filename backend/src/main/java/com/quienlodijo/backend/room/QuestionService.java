package com.quienlodijo.backend.room;

import com.quienlodijo.backend.domain.Question;
import com.quienlodijo.backend.domain.Room;
import com.quienlodijo.backend.domain.RoomPlayer;
import com.quienlodijo.backend.domain.RoomStatus;
import com.quienlodijo.backend.domain.User;
import com.quienlodijo.backend.repository.QuestionRepository;
import com.quienlodijo.backend.repository.RoomPlayerRepository;
import com.quienlodijo.backend.room.dto.RoomStateResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fase de preguntas previa a la partida (spec.md US-3, plan.md §4, T013-T014).
 *
 * <p>Cuando el último jugador pendiente envía sus preguntas, se asigna un orden de
 * juego aleatorio a todas ellas y la sala pasa a {@code IN_PROGRESS}. La creación de
 * la primera {@code Round} (fase ANSWERING) es responsabilidad del motor de rondas de
 * la Fase 4 (T016); aquí solo se deja la sala lista para que ese motor arranque.
 */
@Service
public class QuestionService {

    private final RoomService roomService;
    private final RoomPlayerRepository roomPlayerRepository;
    private final QuestionRepository questionRepository;
    private final SecureRandom random = new SecureRandom();

    public QuestionService(
            RoomService roomService, RoomPlayerRepository roomPlayerRepository, QuestionRepository questionRepository) {
        this.roomService = roomService;
        this.roomPlayerRepository = roomPlayerRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional
    public RoomStateResponse submitQuestions(String code, User user, List<String> questions) {
        Room room = roomService.getRoomOrThrow(code);
        if (room.getStatus() != RoomStatus.COLLECTING_QUESTIONS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La sala no está en fase de preguntas");
        }

        RoomPlayer player =
                roomPlayerRepository
                        .findByRoomAndUser(room, user)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta sala"));

        if (questionRepository.existsByRoomAndAuthorPlayer(room, player)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya has enviado tus preguntas");
        }
        if (questions.size() != room.getQuestionsPerPlayer()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes enviar exactamente " + room.getQuestionsPerPlayer() + " pregunta(s)");
        }

        for (String text : questions) {
            questionRepository.save(
                    Question.builder()
                            .room(room)
                            .authorPlayer(player)
                            .text(text.trim())
                            .discarded(false)
                            .build());
        }

        startGameIfEveryoneSubmitted(room);

        return roomService.getRoomState(code);
    }

    private void startGameIfEveryoneSubmitted(Room room) {
        int playerCount = roomPlayerRepository.findByRoom(room).size();
        long expectedQuestions = (long) playerCount * room.getQuestionsPerPlayer();
        if (questionRepository.countByRoom(room) < expectedQuestions) {
            return; // aún faltan jugadores por enviar sus preguntas
        }

        List<Question> allQuestions = questionRepository.findByRoom(room);
        Collections.shuffle(allQuestions, random);
        for (int i = 0; i < allQuestions.size(); i++) {
            allQuestions.get(i).setPlayOrder(i);
        }
        questionRepository.saveAll(allQuestions);

        room.setStatus(RoomStatus.IN_PROGRESS);
        room.setStartedAt(Instant.now());
        roomService.saveRoom(room);
        roomService.broadcastRoomState(room);

        // TODO (Fase 4, T016): arrancar aquí la primera Round (fase ANSWERING) del motor de rondas.
    }
}
