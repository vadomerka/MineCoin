package com.minecoin.user.infrastructure.web.dto;

import com.minecoin.user.domain.User;
import java.util.UUID;

public record UserRefResponse(UUID id, String username) {

    public static UserRefResponse from(User user) {
        return new UserRefResponse(user.getId(), user.getUsername());
    }
}
