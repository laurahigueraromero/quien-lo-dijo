package com.quienlodijo.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad mínima para la Fase 0 (setup del proyecto).
 *
 * TODO (Fase 1 / T005-T006): sustituir por autenticación JWT real:
 *  - permitAll() en /api/auth/** (registro/login) y en el handshake de /ws
 *  - authenticated() en el resto de /api/**
 *  - filtro JWT que valide el token en cada petición
 *
 * De momento se permite todo para poder levantar y probar el esqueleto del
 * proyecto (REST + WebSocket) sin bloquear por falta de credenciales.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
