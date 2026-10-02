package com.minecoin.finance.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @NotBlank @Size(max = 32) String toUsername,
        @NotNull @Positive Long amount) {
}
