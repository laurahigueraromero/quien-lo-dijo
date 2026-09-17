package com.quienlodijo.backend.round.dto;

/** Payload que el cliente envía a /app/rooms/{code}/bet (spec.md US-5, T022). */
public record BetRequest(Long candidateUserId, int amount) {}
