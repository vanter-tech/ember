package com.vanter.ember.hub.control;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Hands the Hub's freshly-generated first-run admin password from {@code HubProvisioningRunner}
 * (inside the embedded Spring context) to {@link HubControlServer} (outside it, in the
 * orchestrating sidecar JVM) without it ever touching disk (F-15 — the cloud used to send the
 * admin's real bcrypt hash over the wire instead).
 *
 * <p>Lives for the life of the sidecar process: survives a Detener/Iniciar cycle (the embedded
 * Spring context restarts; this holder, created once in {@code EmberApplication.runHubSidecar},
 * does not). It does NOT survive a full process restart — {@code HubProvisioningRunner} only
 * ever runs once per Hub (guarded by "does the restaurant already exist locally"), so a fresh
 * process after that point never repopulates it. If the operator never acknowledges the
 * credential (via {@code DELETE /api/first-run-credentials}) before a full restart, it is lost —
 * a deliberate trade-off for never persisting it (see report for F-15).
 */
public final class FirstRunCredentialHolder {

    public record Credential(String email, String password) {}

    private final AtomicReference<Credential> value = new AtomicReference<>();

    public void set(String email, String password) {
        value.set(new Credential(email, password));
    }

    public Credential get() {
        return value.get();
    }

    public void clear() {
        value.set(null);
    }
}
