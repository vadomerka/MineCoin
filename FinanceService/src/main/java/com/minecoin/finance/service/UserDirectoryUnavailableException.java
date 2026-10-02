package com.minecoin.finance.service;

public class UserDirectoryUnavailableException extends RuntimeException {

    public UserDirectoryUnavailableException(Throwable cause) {
        super("User service is unavailable", cause);
    }
}
