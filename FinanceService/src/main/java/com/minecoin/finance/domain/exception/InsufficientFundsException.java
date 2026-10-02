package com.minecoin.finance.domain.exception;

public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException() {
        super("INSUFFICIENT_FUNDS", "Insufficient funds");
    }
}
