package com.quienlodijo.backend.round.dto;

import java.util.List;

/**
 * Payload del evento WS "ROUND_BETTING_STARTED" (plan.md §5, T024).
 * {@code candidateUserIds} son los ids de {@code User} de todos los jugadores
 * salvo el autor real — nunca se revela quién es; el propio cliente deduce
 * "soy el autor" si su userId NO aparece en esta lista (spec.md US-5).
 */
public record RoundBettingStartedEvent(
        Long roundId, String shownAnswerText, List<Long> candidateUserIds, String bettingEndsAt) {}
