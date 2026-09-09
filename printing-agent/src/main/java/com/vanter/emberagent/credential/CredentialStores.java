package com.vanter.emberagent.credential;

/** Picks the right {@link CredentialStore} for the host OS: DPAPI on Windows, plaintext elsewhere. */
public final class CredentialStores {

    private CredentialStores() {}

    public static CredentialStore forThisMachine() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? new DpapiCredentialStore() : new PlaintextCredentialStore();
    }
}
