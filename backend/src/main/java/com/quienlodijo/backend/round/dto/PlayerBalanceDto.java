package com.quienlodijo.backend.round.dto;

/** Saldo de un jugador tras resolverse una ronda (T029). */
public record PlayerBalanceDto(Long userId, int saldoFichas, boolean eliminated) {}
