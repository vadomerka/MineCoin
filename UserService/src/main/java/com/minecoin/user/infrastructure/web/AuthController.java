package com.minecoin.user.infrastructure.web;

import com.minecoin.user.domain.User;
import com.minecoin.user.infrastructure.security.JwtTokenService;
import com.minecoin.user.infrastructure.web.dto.LoginRequest;
import com.minecoin.user.infrastructure.web.dto.RegisterRequest;
import com.minecoin.user.infrastructure.web.dto.TokenResponse;
import com.minecoin.user.infrastructure.web.dto.UserResponse;
import com.minecoin.user.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(
                request.username(), request.email(), request.password(), request.displayName());
        return ResponseEntity.created(URI.create("/api/users/" + user.getId()))
                .body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request.username(), request.password());
        return TokenResponse.from(jwtTokenService.issue(user));
    }
}
