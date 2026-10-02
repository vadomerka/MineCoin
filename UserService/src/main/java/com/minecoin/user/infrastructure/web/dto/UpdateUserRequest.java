package com.minecoin.user.infrastructure.web.dto;

import com.minecoin.user.service.UpdateUserCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 64) String displayName) {

    public UpdateUserCommand toCommand() {
        return new UpdateUserCommand(email, displayName);
    }
}
