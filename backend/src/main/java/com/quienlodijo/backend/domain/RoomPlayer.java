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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Participación de un {@link User} en un {@link Room} concreto (spec.md §6-§7).
 * Saldo inicial de 50 fichas; eliminated=true cuando el saldo llega a 0.
 */
@Entity
@Table(
        name = "room_players",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = {"room_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomPlayer {

    public static final int SALDO_INICIAL = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int saldoFichas;

    @Column(nullable = false)
    private boolean eliminated;

    @Column(nullable = false)
    private Instant joinedAt;
}
