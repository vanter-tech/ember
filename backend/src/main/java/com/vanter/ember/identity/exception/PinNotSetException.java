package com.vanter.ember.identity.exception;

public class PinNotSetException extends RuntimeException {
    public PinNotSetException() { super("No PIN is set for this account."); }
}
