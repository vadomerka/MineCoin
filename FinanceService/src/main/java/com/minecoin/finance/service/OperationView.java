package com.minecoin.finance.service;

import com.minecoin.finance.domain.OperationType;
import java.time.Instant;
import java.util.UUID;

public record OperationView(
        UUID id,
        OperationType type,
        OperationDirection direction,
        long amount,
        UUID counterpartyUserId,
        String counterpartyUsername,
        Instant createdAt) {
}
