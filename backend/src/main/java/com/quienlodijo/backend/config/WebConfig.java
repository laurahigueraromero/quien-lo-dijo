package com.quienlodijo.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para llamadas directas a la API REST sin pasar por un proxy (ej. pruebas manuales,
 * o el frontend Vite en dev cuando no usa su proxy). Patrón comodín por la misma razón que
 * en WebSocketConfig: el origen real (IP LAN del móvil, host de docker-compose, etc.) no se
 * conoce de antemano. Aceptable para desarrollo/red local.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
