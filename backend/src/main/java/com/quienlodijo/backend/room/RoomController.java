package com.quienlodijo.backend.room;

import com.quienlodijo.backend.domain.User;
import com.quienlodijo.backend.room.dto.CreateRoomRequest;
import com.quienlodijo.backend.room.dto.RoomStateResponse;
import com.quienlodijo.backend.room.dto.SubmitQuestionsRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Crear/unirse/arrancar/cerrar salas y enviar preguntas (spec.md US-1/US-2/US-3, plan.md §4, T008-T010, T013). */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final QuestionService questionService;

    public RoomController(RoomService roomService, QuestionService questionService) {
        this.roomService = roomService;
        this.questionService = questionService;
    }

    @PostMapping
    public ResponseEntity<RoomStateResponse> create(
            @AuthenticationPrincipal User host, @Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.createRoom(host, request.questionsPerPlayer()));
    }

    @GetMapping("/{code}")
    public RoomStateResponse get(@PathVariable String code) {
        return roomService.getRoomState(code);
    }

    @PostMapping("/{code}/join")
    public RoomStateResponse join(@PathVariable String code, @AuthenticationPrincipal User user) {
        return roomService.joinRoom(code, user);
    }

    @PostMapping("/{code}/start")
    public RoomStateResponse start(@PathVariable String code, @AuthenticationPrincipal User user) {
        return roomService.startRoom(code, user);
    }

    @PostMapping("/{code}/questions")
    public RoomStateResponse submitQuestions(
            @PathVariable String code, @AuthenticationPrincipal User user, @Valid @RequestBody SubmitQuestionsRequest request) {
        return questionService.submitQuestions(code, user, request.questions());
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> close(@PathVariable String code, @AuthenticationPrincipal User user) {
        roomService.closeRoom(code, user);
        return ResponseEntity.noContent().build();
    }
}
