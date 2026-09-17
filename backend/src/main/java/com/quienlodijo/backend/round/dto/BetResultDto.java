package com.quienlodijo.backend.round.dto;

/** Detalle de una apuesta ya resuelta (spec.md §6, T026/T029). */
public record BetResultDto(Long bettorUserId, Long candidateUserId, int amount, boolean correct, boolean autoAssigned) {}
