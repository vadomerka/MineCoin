package com.minecoin.user.domain.exception;

public class EmailTakenException extends DomainException {

    public EmailTakenException(String email) {
        super("EMAIL_TAKEN", "Email '" + email + "' is already taken");
    }
}
