package com.quienlodijo.backend.room;

import com.quienlodijo.backend.domain.Room;
import com.quienlodijo.backend.domain.RoomPlayer;
import com.quienlodijo.backend.domain.RoomStatus;
import com.quienlodijo.backend.domain.User;
import com.quienlodijo.backend.repository.RoomPlayerRepository;
import com.quienlodijo.backend.repository.RoomRepository;
import com.quienlodijo.backend.room.dto.RoomEvent;
import com.quienlodijo.backend.room.dto.RoomPlayerDto;
import com.quienlodijo.backend.room.dto.RoomStateResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creación, unión y arranque/cierre de salas (spec.md US-1/US-2, plan.md §4).
 *
 * <p>El estado en vivo se propaga por WebSocket enviando un {@link RoomEvent} de tipo
 * "ROOM_STATE" a /topic/rooms/{code} tras cada cambio (T011); el snapshot inicial lo
 * obtiene el cliente vía REST ({@link #getRoomState}) antes de suscribirse al topic.
 */
@Service
public class RoomService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin I/O/0/1 para evitar confusiones
    private static final int CODE_LENGTH = 6;
    public static final int MIN_PLAYERS_TO_START = 3;

    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecureRandom random = new SecureRandom();

    public RoomService(
            RoomRepository roomRepository,
            RoomPlayerRepository roomPlayerRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.roomPlayerRepository = roomPlayerRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public RoomStateResponse createRoom(User host, int questionsPerPlayer) {
        Room room =
                Room.builder()
                        .code(generateUniqueCode())
                        .host(host)
                        .status(RoomStatus.WAITING)
                        .questionsPerPlayer(questionsPerPlayer)
                        .createdAt(Instant.now())
                        .build();
        room = roomRepository.save(room);
        addPlayer(room, host);
        return toRoomState(room);
    }

    @Transactional(readOnly = true)
    public RoomStateResponse getRoomState(String code) {
        return toRoomState(getRoomOrThrow(code));
    }

    @Transactional
    public RoomStateResponse joinRoom(String code, User user) {
        Room room = getRoomOrThrow(code);
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La partida ya ha comenzado");
        }
        if (!roomPlayerRepository.existsByRoomAndUser(room, user)) {
            addPlayer(room, user);
        }
        RoomStateResponse state = toRoomState(room);
        broadcastRoomState(code, state);
        return state;
    }

    @Transactional
    public RoomStateResponse startRoom(String code, User user) {
        Room room = getRoomOrThrow(code);
        requireHost(room, user);
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La partida ya ha comenzado");
        }
        int playerCount = roomPlayerRepository.findByRoom(room).size();
        if (playerCount < MIN_PLAYERS_TO_START) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Se necesitan al menos " + MIN_PLAYERS_TO_START + " jugadores para empezar");
        }
        room.setStatus(RoomStatus.COLLECTING_QUESTIONS);
        room = roomRepository.save(room);
        RoomStateResponse state = toRoomState(room);
        broadcastRoomState(code, state);
        return state;
    }

    @Transactional
    public void closeRoom(String code, User user) {
        Room room = getRoomOrThrow(code);
        requireHost(room, user);
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Solo se puede cerrar una sala que no ha empezado");
        }
        messagingTemplate.convertAndSend(topicFor(code), new RoomEvent("ROOM_CLOSED", null));
        roomPlayerRepository.deleteAll(roomPlayerRepository.findByRoom(room));
        roomRepository.delete(room);
    }

    private void addPlayer(Room room, User user) {
        RoomPlayer player =
                RoomPlayer.builder()
                        .room(room)
                        .user(user)
                        .saldoFichas(RoomPlayer.SALDO_INICIAL)
                        .eliminated(false)
                        .joinedAt(Instant.now())
                        .build();
        roomPlayerRepository.save(player);
    }

    /** Público para que otros servicios de la misma partida (ej. QuestionService, Fase 3) reutilicen la búsqueda. */
    public Room getRoomOrThrow(String code) {
        return roomRepository
                .findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala no encontrada"));
    }

    /** Reemite el snapshot actual de la sala por WebSocket (T011); usado tras cambios hechos desde otros servicios. */
    public void broadcastRoomState(Room room) {
        broadcastRoomState(room.getCode(), toRoomState(room));
    }

    /** Persiste cambios hechos sobre un {@link Room} desde otro servicio (ej. QuestionService, Fase 3). */
    public Room saveRoom(Room room) {
        return roomRepository.save(room);
    }

    private void requireHost(Room room, User user) {
        if (!room.getHost().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el anfitrión puede hacer esto");
        }
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (roomRepository.existsByCode(code));
        return code;
    }

    private RoomStateResponse toRoomState(Room room) {
        List<RoomPlayerDto> players =
                roomPlayerRepository.findByRoom(room).stream()
                        .map(
                                p ->
                                        new RoomPlayerDto(
                                                p.getUser().getId(),
                                                p.getUser().getUsername(),
                                                p.getSaldoFichas(),
                                                p.isEliminated()))
                        .toList();
        return new RoomStateResponse(
                room.getCode(), room.getStatus().name(), room.getQuestionsPerPlayer(), room.getHost().getId(), players);
    }

    private void broadcastRoomState(String code, RoomStateResponse state) {
        messagingTemplate.convertAndSend(topicFor(code), new RoomEvent("ROOM_STATE", state));
    }

    private String topicFor(String code) {
        return "/topic/rooms/" + code;
    }
}
