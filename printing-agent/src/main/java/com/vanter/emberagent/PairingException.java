package com.vanter.emberagent;

/** Thrown when a pairing-code redemption fails; the message is user-facing (Spanish). */
public class PairingException extends RuntimeException {
    public PairingException(String message) {
        super(message);
    }
}
