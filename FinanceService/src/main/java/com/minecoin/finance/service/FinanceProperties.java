package com.minecoin.finance.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.finance")
public record FinanceProperties(long maxOperationAmount) {

    public FinanceProperties {
        if (maxOperationAmount <= 0) {
            throw new IllegalArgumentException("MAX_OPERATION_AMOUNT must be positive");
        }
    }
}
