package com.vanter.ember.hub.bootstrap;

/** Anything that stops the portable local MinIO from being ready to accept connections. */
public class PortableMinioException extends Exception {

    public PortableMinioException(String message) {
        super(message);
    }

    public PortableMinioException(String message, Throwable cause) {
        super(message, cause);
    }
}
