package com.quienlodijo.backend.round.dto;

import java.util.List;

/**
 * Payload del evento WS "ROUND_RESOLVED" (plan.md §5/§6, T029). Aquí sí se revela
 * quién era el autor — la ronda ya ha terminado, el misterio ya no aplica.
 */
public record RoundResolvedEvent(
        Long roundId,
        Long authorUserId,
        List<BetResultDto> bets,
        List<PlayerBalanceDto> balances,
        List<Long> newlyEliminatedUserIds) {}
