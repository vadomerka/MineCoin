package com.minecoin.user.domain.exception;

import java.util.UUID;

public class UserDeletedException extends DomainException {

    public UserDeletedException(UUID id) {
        super("USER_DELETED", "User " + id + " is deleted");
    }
}
