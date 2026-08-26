package com.vanter.ember.hub.license;

/** Anything that stops Ember Hub from starting under a valid, matching license. */
public class InvalidLicenseException extends Exception {

    public InvalidLicenseException(String message) {
        super(message);
    }

    public InvalidLicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
