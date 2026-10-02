package com.minecoin.finance.infrastructure.web.dto;

import com.minecoin.finance.domain.OperationType;
import com.minecoin.finance.service.OperationDirection;
import com.minecoin.finance.service.OperationView;
import java.time.Instant;
import java.util.UUID;

public record OperationResponse(
        UUID id,
        OperationType type,
        OperationDirection direction,
        long amount,
        UUID counterpartyId,
        String counterpartyUsername,
        Instant createdAt) {

    public static OperationResponse from(OperationView view) {
        return new OperationResponse(
                view.id(),
                view.type(),
                view.direction(),
                view.amount(),
                view.counterpartyUserId(),
                view.counterpartyUsername(),
                view.createdAt());
    }
}
