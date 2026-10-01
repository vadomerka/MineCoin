package com.minecoin.user.infrastructure.web.dto;

import com.minecoin.user.domain.Role;
import com.minecoin.user.domain.User;
import com.minecoin.user.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        Role role,
        UserStatus status,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
