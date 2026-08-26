package com.vanter.ember.hub.bootstrap;

import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HardwareFingerprintService;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.hub.license.LicenseKeyParser;
import com.vanter.ember.hub.license.LicenseService;
import java.security.PublicKey;

/**
 * Validates the license and starts/stops the portable Postgres server, without ever calling
 * {@code System.exit} — unlike the old inline {@code EmberApplication.bootstrapHub()}, a failure
 * here has to be shown in {@link com.vanter.ember.hub.dashboard.HubDashboard} and be retryable,
 * not crash the JVM. Registers a JVM shutdown hook on a successful start so the portable Postgres
 * process tree is always stopped cleanly on exit, however the JVM exits.
 */
public class HubBootstrapRunner {

    private final HubProperties properties;
    private PortableDatabaseBootstrap dbBootstrap;
    private Thread shutdownHook;

    public HubBootstrapRunner(HubProperties properties) {
        this.properties = properties;
    }

    public void startServices() throws InvalidLicenseException, PortableDatabaseException {
        PublicKey publicKey = LicenseKeyParser.loadPublicKey(properties.publicKeyFile());
        LicenseService licenseService = new LicenseService(
                properties.licenseFile(),
                publicKey,
                new LicenseKeyParser(),
                new HardwareFingerprintService(),
                new HubStateStore(properties.stateFile()));
        licenseService.validateOrActivate();

        dbBootstrap = new PortableDatabaseBootstrap(
                properties.dataDir(), properties.postgresBinDir(), properties.postgresPort());
        dbBootstrap.ensureRunning();

        shutdownHook = new Thread(this::stopServicesQuietly, "hub-db-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /** Stops Postgres without exiting the JVM, so {@link #startServices()} can be called again. */
    public void stopServices() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down — the hook will run on its own, nothing to remove.
            }
            shutdownHook = null;
        }
        stopServicesQuietly();
    }

    private void stopServicesQuietly() {
        if (dbBootstrap != null) {
            try {
                dbBootstrap.stop();
            } catch (PortableDatabaseException ignored) {
                // Best-effort on shutdown — there's nowhere left to surface this to the user.
            }
            dbBootstrap = null;
        }
    }
}
