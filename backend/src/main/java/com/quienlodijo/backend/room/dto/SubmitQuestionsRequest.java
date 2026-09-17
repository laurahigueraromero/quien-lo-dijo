package com.quienlodijo.backend.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Las N preguntas que un jugador aporta antes de la partida (spec.md US-3, T013). */
public record SubmitQuestionsRequest(@NotEmpty List<@NotBlank String> questions) {}
