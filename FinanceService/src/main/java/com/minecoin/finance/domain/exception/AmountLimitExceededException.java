package com.minecoin.finance.domain.exception;

public class AmountLimitExceededException extends DomainException {

    public AmountLimitExceededException(long maxAmount) {
        super("AMOUNT_LIMIT_EXCEEDED", "Amount must not exceed " + maxAmount);
    }
}
