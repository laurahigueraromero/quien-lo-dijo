package com.quienlodijo.backend.security;

import java.security.Principal;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Autentica el handshake STOMP: el cliente manda el JWT en el header nativo
 * "Authorization" del frame CONNECT (plan.md §5/§7, T006). Si es válido, asocia
 * el id de usuario como Principal de la sesión WebSocket, disponible luego en
 * los {@code @MessageMapping} de las salas (Fase 2 en adelante).
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public StompAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                if (jwtService.isValid(token)) {
                    Long userId = jwtService.extractUserId(token);
                    accessor.setUser((Principal) userId::toString);
                    // Reconstruir el Message con las cabeceras ya mutadas: mutar el accessor no
                    // basta por sí solo para garantizar que el Principal quede asociado a la
                    // sesión (lo necesitan los destinos de usuario, ej. @SendToUser en T017).
                    return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
                }
            }
        }
        return message;
    }
}
