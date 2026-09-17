package com.quienlodijo.backend.domain;

/** Ciclo de vida de una sala/partida (plan.md §3). */
public enum RoomStatus {
    WAITING,
    COLLECTING_QUESTIONS,
    IN_PROGRESS,
    FINISHED,
    CANCELLED
}
