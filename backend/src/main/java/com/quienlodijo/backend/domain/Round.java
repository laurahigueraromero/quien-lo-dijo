package com.quienlodijo.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una ronda de juego: pregunta -> respuestas -> apuesta -> resolución (plan.md §3).
 * selectedAnswer/authorPlayer quedan nulos hasta que se sortea la respuesta al cerrar
 * la fase ANSWERING (T021).
 */
@Entity
@Table(name = "rounds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Round {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoundStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_answer_id")
    private Answer selectedAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_player_id")
    private RoomPlayer authorPlayer;

    private Instant answeringEndsAt;

    private Instant bettingEndsAt;

    private Instant resolvedAt;
}
