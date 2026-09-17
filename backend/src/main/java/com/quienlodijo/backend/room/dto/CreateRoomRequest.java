package com.quienlodijo.backend.room.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateRoomRequest(@Min(1) @Max(20) int questionsPerPlayer) {}
