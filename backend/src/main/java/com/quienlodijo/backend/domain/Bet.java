package com.quienlodijo.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Apuesta de un jugador sobre quién cree que es el autor de la respuesta mostrada
 * (spec.md US-5). amount se descuenta del saldo del apostante en el momento de apostar
 * (plan.md §6, nota de implementación); correct se rellena al resolver la ronda (T026).
 */
@Entity
@Table(
        name = "bets",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = {"round_id", "bettor_player_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bet {

    public static final int APUESTA_MINIMA = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bettor_player_id", nullable = false)
    private RoomPlayer bettor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_player_id", nullable = false)
    private RoomPlayer candidate;

    @Column(nullable = false)
    private int amount;

    private Boolean correct;

    @Column(nullable = false)
    private boolean autoAssigned;
}
