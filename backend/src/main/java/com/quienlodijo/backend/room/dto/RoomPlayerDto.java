package com.quienlodijo.backend.room.dto;

public record RoomPlayerDto(Long userId, String username, int saldoFichas, boolean eliminated) {}
