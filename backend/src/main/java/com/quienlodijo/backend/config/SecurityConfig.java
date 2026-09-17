package com.quienlodijo.backend.config;

import com.quienlodijo.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad basada en JWT, sin sesión de servidor (plan.md §7, T005-T006).
 * - /api/auth/** y /api/health son públicos.
 * - /ws/** se deja pasar a nivel HTTP: la autenticación del WebSocket ocurre
 *   en el frame STOMP CONNECT (ver StompAuthChannelInterceptor), no en el handshake HTTP.
 * - /h2-console/** solo tiene sentido en el perfil dev.
 * - /error debe ser público: un ResponseStatusException (ej. 409/401 de AuthService) se
 *   resuelve internamente con sendError(), que Tomcat reenvía como un dispatch a /error;
 *   si esa ruta no está permitida, Security la bloquea con 403 y pisa el código real.
 * - El resto de /api/** requiere el JWT válido que añade JwtAuthenticationFilter.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/api/auth/**", "/api/health", "/ws/**", "/h2-console/**", "/error")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
