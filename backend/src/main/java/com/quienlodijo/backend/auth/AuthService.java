package com.quienlodijo.backend.auth;

import com.quienlodijo.backend.auth.dto.AuthResponse;
import com.quienlodijo.backend.auth.dto.LoginRequest;
import com.quienlodijo.backend.auth.dto.RegisterRequest;
import com.quienlodijo.backend.domain.User;
import com.quienlodijo.backend.repository.UserRepository;
import com.quienlodijo.backend.security.JwtService;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Alta y login de usuarios (spec.md §3, plan.md §7, T005). */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya está en uso");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        }

        User user =
                User.builder()
                        .username(request.username())
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .createdAt(Instant.now())
                        .build();
        user = userRepository.save(user);

        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByUsername(request.username())
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getUsername());
    }
}
