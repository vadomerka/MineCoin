package com.minecoin.user.domain.exception;

import java.util.UUID;

public class UserBlockedException extends DomainException {

    public UserBlockedException(UUID id) {
        super("USER_BLOCKED", "User " + id + " is blocked");
    }
}
