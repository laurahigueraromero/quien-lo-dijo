package com.quienlodijo.backend.round.dto;

/**
 * Error de una acción WebSocket, enviado solo al usuario que la disparó vía
 * /user/queue/errors (T017 — sin esto, una excepción en un @MessageMapping se
 * queda solo en el log del servidor y el cliente nunca se entera).
 */
public record WsErrorResponse(String message) {}
