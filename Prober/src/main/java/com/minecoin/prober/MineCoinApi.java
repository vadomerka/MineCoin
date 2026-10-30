package com.minecoin.prober;

import java.time.Instant;

public interface MineCoinApi {

    void home();

    void register(String username, String password);

    Session login(String username, String password);

    void wallet(String token);

    void deposit(String token, long amount);

    void transfer(String token, String toUsername, long amount);

    record Session(String token, Instant expiresAt) {
    }

    class ProbeFailedException extends RuntimeException {

        private final int status;

        public ProbeFailedException(String message, int status) {
            super(message);
            this.status = status;
        }

        public ProbeFailedException(String message, Throwable cause) {
            super(message, cause);
            this.status = 0;
        }

        public int status() {
            return status;
        }
    }
}
