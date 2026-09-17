package com.quienlodijo.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pregunta introducida por un jugador antes de la partida (spec.md US-3).
 * discarded=true cuando el autor queda eliminado antes de que le toque su turno (spec.md §6/§9).
 */
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_player_id", nullable = false)
    private RoomPlayer authorPlayer;

    @Lob
    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private boolean discarded;

    /** Orden aleatorio de juego asignado al arrancar la fase de rondas (plan.md §3, T014). */
    private Integer playOrder;
}
