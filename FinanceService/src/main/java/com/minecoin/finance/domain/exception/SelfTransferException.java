package com.minecoin.finance.domain.exception;

public class SelfTransferException extends DomainException {

    public SelfTransferException() {
        super("SELF_TRANSFER", "Cannot transfer to yourself");
    }
}
