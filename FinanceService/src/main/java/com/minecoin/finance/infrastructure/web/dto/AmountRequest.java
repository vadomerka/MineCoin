package com.minecoin.finance.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AmountRequest(@NotNull @Positive Long amount) {
}
