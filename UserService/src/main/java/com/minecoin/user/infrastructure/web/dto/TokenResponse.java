package com.minecoin.user.infrastructure.web.dto;

import com.minecoin.user.infrastructure.security.JwtTokenService.IssuedToken;
import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {

    public static TokenResponse from(IssuedToken token) {
        return new TokenResponse(token.value(), "Bearer", token.expiresAt());
    }
}
