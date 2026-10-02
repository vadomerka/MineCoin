package com.minecoin.user.domain.exception;

public class UsernameTakenException extends DomainException {

    public UsernameTakenException(String username) {
        super("USERNAME_TAKEN", "Username '" + username + "' is already taken");
    }
}
