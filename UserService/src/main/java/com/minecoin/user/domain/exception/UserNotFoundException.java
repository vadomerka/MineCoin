package com.minecoin.user.domain.exception;

import java.util.UUID;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException(UUID id) {
        super("USER_NOT_FOUND", "User " + id + " not found");
    }

    public UserNotFoundException(String username) {
        super("USER_NOT_FOUND", "User '" + username + "' not found");
    }
}
