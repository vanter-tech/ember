package com.vanter.ember.identity.exception;

public class PinLockedException extends RuntimeException {
    public PinLockedException() { super("Too many failed PIN attempts. Use your password."); }
}
