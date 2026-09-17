package com.quienlodijo.backend.security;

import com.quienlodijo.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida el JWT del header {@code Authorization: Bearer ...} en cada petición REST
 * (plan.md §7, T006). Si es válido, deja el {@code User} autenticado en el
 * SecurityContext para que los controladores lo lean con {@code @AuthenticationPrincipal}.
 * No hay roles/permisos finos en este MVP: solo "autenticado o no".
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                Long userId = jwtService.extractUserId(token);
                userRepository
                        .findById(userId)
                        .ifPresent(
                                user -> {
                                    var authentication =
                                            new UsernamePasswordAuthenticationToken(user, null, List.of());
                                    SecurityContextHolder.getContext().setAuthentication(authentication);
                                });
            }
        }
        filterChain.doFilter(request, response);
    }
}
