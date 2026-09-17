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
 * - /queue/** también está habilitado en el broker: lo necesitan los destinos de usuario
 *   (@SendToUser, ej. /user/queue/errors en T017), que Spring reescribe internamente a una
 *   cola /queue/**-por-sesión antes de reenviarla al broker.
 * - Orígenes permitidos: patrón comodín. El navegador manda su propio origen (ej. la IP LAN
 *   del PC si se accede desde el móvil, o localhost:5173 en dev) y Spring lo valida contra
 *   esta lista para el handshake, algo independiente de que nginx haga de reverse proxy
 *   (docker-compose) — de ahí no poder fijar aquí un único host conocido de antemano. Aceptable
 *   para desarrollo/red local; en un despliegue real conviene acotarlo a los dominios propios.
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
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
