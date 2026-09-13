package com.vanter.ember.hub.control;

import com.vanter.ember.EmberApplication;
import com.vanter.ember.hub.bootstrap.HubBootProgressListener;
import com.vanter.ember.hub.bootstrap.HubBootstrapRunner;
import com.vanter.ember.hub.bootstrap.PortableDatabaseException;
import com.vanter.ember.hub.bootstrap.PortableMinioException;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.dashboard.LicenseFileInstaller;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.InvalidLicenseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Wraps {@link HubBootstrapRunner} + a {@code SpringApplication} run exactly like
 * {@code HubDashboard.onStart}/{@code onStop} used to, tracking a {@link ServicePhase} per
 * service so {@link HubControlServer} can report real progress instead of a Swing label mutated
 * in place. No business logic changes — same calls, same order, same exceptions.
 */
public final class DefaultHubOrchestrator implements HubOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DefaultHubOrchestrator.class);

    private final HubProperties properties;
    private final HubBootstrapRunner bootstrapRunner;
    private final HubStateStore stateStore;
    private final String[] fallbackArgs = new String[0];

    private volatile ServicePhase postgresPhase = ServicePhase.STOPPED;
    private volatile String postgresError;
    private volatile ServicePhase minioPhase = ServicePhase.STOPPED;
    private volatile String minioError;
    private volatile ServicePhase serverPhase = ServicePhase.STOPPED;
    private volatile String serverError;
    private volatile ConfigurableApplicationContext context;

    public DefaultHubOrchestrator(HubProperties properties) {
        this.properties = properties;
        this.bootstrapRunner = new HubBootstrapRunner(properties);
        this.stateStore = new HubStateStore(properties.stateFile());
    }

    @Override
    public synchronized void start(String[] launchArgs) {
        if (postgresPhase != ServicePhase.STOPPED && postgresPhase != ServicePhase.ERROR) {
            return; // already starting/running — HubControlServer turns this into a 409.
        }
        postgresPhase = ServicePhase.STARTING;
        postgresError = null;
        minioPhase = ServicePhase.STOPPED;
        minioError = null;
        serverPhase = ServicePhase.STOPPED;
        serverError = null;
        String[] args = launchArgs != null ? launchArgs : fallbackArgs;
        new Thread(() -> runStart(args), "hub-orchestrator-start").start();
    }

    private void runStart(String[] launchArgs) {
        try {
            bootstrapRunner.startServices(new HubBootProgressListener() {
                @Override
                public void onPostgresStarting() {
                    postgresPhase = ServicePhase.STARTING;
                }

                @Override
                public void onPostgresReady() {
                    postgresPhase = ServicePhase.RUNNING;
                }

                @Override
                public void onMinioStarting() {
                    minioPhase = ServicePhase.STARTING;
                }

                @Override
                public void onMinioReady() {
                    minioPhase = ServicePhase.RUNNING;
                }
            });

            serverPhase = ServicePhase.STARTING;
            SpringApplication app = new SpringApplication(EmberApplication.class);
            app.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> serverPhase = ServicePhase.RUNNING);
            context = app.run(launchArgs);
        } catch (InvalidLicenseException e) {
            // Licencia inválida bloquea todo antes de que Postgres arranque siquiera — se reporta
            // en la card de Postgres porque es el primer paso de la secuencia real, no porque el
            // problema sea de Postgres (no hay una 4ª card de licencia-bloqueante en el diseño
            // aprobado; la card de licencia por sí sola solo refleja el estado ya persistido).
            log.error("Ember Hub no pudo iniciar: licencia inválida", e);
            postgresPhase = ServicePhase.ERROR;
            postgresError = e.getMessage();
        } catch (PortableDatabaseException e) {
            log.error("Ember Hub no pudo iniciar Postgres", e);
            postgresPhase = ServicePhase.ERROR;
            postgresError = e.getMessage();
        } catch (PortableMinioException e) {
            log.error("Ember Hub no pudo iniciar MinIO", e);
            minioPhase = ServicePhase.ERROR;
            minioError = e.getMessage();
        } catch (Exception e) {
            log.error("Ember Hub no pudo iniciar el servidor", e);
            serverPhase = ServicePhase.ERROR;
            serverError = e.getMessage();
        }
    }

    @Override
    public synchronized void stop() {
        if (serverPhase != ServicePhase.RUNNING && serverPhase != ServicePhase.ERROR
                && postgresPhase != ServicePhase.RUNNING && postgresPhase != ServicePhase.ERROR) {
            return; // nothing to stop — HubControlServer turns this into a 409.
        }
        serverPhase = ServicePhase.STOPPING;
        postgresPhase = ServicePhase.STOPPING;
        minioPhase = ServicePhase.STOPPING;
        new Thread(this::runStop, "hub-orchestrator-stop").start();
    }

    private void runStop() {
        if (context != null) {
            context.close();
            context = null;
        }
        serverPhase = ServicePhase.STOPPED;
        serverError = null;
        bootstrapRunner.stopServices();
        minioPhase = ServicePhase.STOPPED;
        minioError = null;
        postgresPhase = ServicePhase.STOPPED;
        postgresError = null;
    }

    @Override
    public void installLicense(Path source) throws IOException {
        LicenseFileInstaller.install(source, properties.licenseFile());
    }

    @Override
    public synchronized void removeLicense() throws IOException {
        Files.deleteIfExists(properties.licenseFile());
        Files.deleteIfExists(properties.stateFile());
    }

    @Override
    public HubStatusSnapshot snapshot() {
        LicenseSnapshot license = stateStore.load()
                .map(this::toLicenseSnapshot)
                .orElse(new LicenseSnapshot(LicenseSnapshot.NONE, null, null));
        return new HubStatusSnapshot(
                postgresPhase, postgresError,
                minioPhase, minioError,
                serverPhase, serverError,
                license, properties.serverPort());
    }

    private LicenseSnapshot toLicenseSnapshot(HubState state) {
        String status = state.suspendedSince() != null ? LicenseSnapshot.SUSPENDED : LicenseSnapshot.OK;
        return new LicenseSnapshot(status, state.lastHeartbeatAt(), state.suspendedSince());
    }
}
