package com.quienlodijo.backend.round.dto;

/**
 * Payload del evento WS "ROUND_ANSWERING_STARTED" (plan.md §5, T019).
 * {@code answeringEndsAt} va como ISO-8601 (String) en vez de Instant para no
 * depender de que el conversor de mensajes STOMP tenga registrado el módulo
 * JSR-310 de Jackson.
 */
public record RoundAnsweringStartedEvent(Long roundId, String questionText, String answeringEndsAt) {}
