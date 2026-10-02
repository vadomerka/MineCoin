package com.minecoin.user.domain.exception;

public class UserBlockedException extends DomainException {

    public UserBlockedException() {
        super("USER_BLOCKED", "Account is blocked");
    }
}
