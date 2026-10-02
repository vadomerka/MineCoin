package com.minecoin.finance.domain.exception;

public class RecipientNotFoundException extends DomainException {

    public RecipientNotFoundException(String username) {
        super("RECIPIENT_NOT_FOUND", "Recipient '" + username + "' not found");
    }
}
