package com.vanter.ember.hub.bootstrap;

/** Anything that stops the portable local Postgres from being ready to accept connections. */
public class PortableDatabaseException extends Exception {

    public PortableDatabaseException(String message) {
        super(message);
    }

    public PortableDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
