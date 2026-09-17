package com.quienlodijo.backend.room.dto;

/**
 * Envoltorio de los eventos que se publican en /topic/rooms/{code} (plan.md §5).
 * El cliente distingue el evento por {@code type} (ej. "ROOM_STATE", "ROOM_CLOSED")
 * y castea {@code payload} en consecuencia.
 */
public record RoomEvent(String type, Object payload) {}
