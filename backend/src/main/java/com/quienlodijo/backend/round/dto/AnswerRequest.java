package com.quienlodijo.backend.round.dto;

/** Payload que el cliente envía a /app/rooms/{code}/answer (spec.md US-4, T017). */
public record AnswerRequest(String text) {}
