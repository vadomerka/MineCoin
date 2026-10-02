package com.minecoin.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 32) String username,
        @NotBlank @Size(max = 72) String password) {
}
