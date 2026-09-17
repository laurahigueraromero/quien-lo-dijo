package com.quienlodijo.backend.room.dto;

import java.util.List;

/** Snapshot de una sala (spec.md US-1/US-2, plan.md §4). Usado tanto en la respuesta REST como en el evento WS ROOM_STATE. */
public record RoomStateResponse(
        String code, String status, int questionsPerPlayer, Long hostUserId, List<RoomPlayerDto> players) {}
