package com.quienlodijo.backend.config;

import com.quienlodijo.backend.security.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuración base de STOMP sobre SockJS (plan.md §5).
 * - Los clientes se conectan a /ws.
 * - Los eventos de servidor a cliente se publican en /topic/** (ej. /topic/rooms/{code}).
 * - Las acciones de cliente a servidor llegan con prefijo /app/** (ej. /app/rooms/{code}/answer).
 * - El frame CONNECT se autentica con JWT vía StompAuthChannelInterceptor (T006).
 *
 * La lógica de salas (T011 en adelante) añadirá los @MessageMapping correspondientes;
 * esta clase solo deja preparado el transporte.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
