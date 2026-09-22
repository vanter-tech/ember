package com.vanter.ember;

import com.vanter.ember.hub.backup.BackupConfigStore;
import com.vanter.ember.hub.backup.BackupScheduler;
import com.vanter.ember.hub.backup.HubBackupService;
import com.vanter.ember.hub.backup.HubVersion;
import com.vanter.ember.hub.backup.PostgresTools;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.control.DefaultHubOrchestrator;
import com.vanter.ember.hub.control.FirstRunCredentialHolder;
import com.vanter.ember.hub.control.HubControlServer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EmberApplication {

    public static void main(String[] args) throws IOException {
        if (isHubProfile()) {
            runHubSidecar(args);
            return;
        }
        SpringApplication.run(EmberApplication.class, args);
    }

    /**
     * Runs headless: a {@link HubControlServer} that a separate Tauri shell process polls/calls
     * instead of the old Swing {@code HubDashboard}/{@code HubTrayIcon} reading/mutating state
     * in-process (spec docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md, plan
     * docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md). Prints "PORT=&lt;n&gt;" to
     * stdout once the control server is listening — the Tauri shell reads that single line from
     * this process's stdout to know where to send requests. Auto-starts services immediately,
     * same as {@code HubDashboard.launch}'s old {@code --autostart} argument did (the Tauri shell
     * always passes it, mirroring "Iniciar Ember Hub.cmd"'s prior behavior).
     */
    private static void runHubSidecar(String[] args) throws IOException {
        HubProperties properties = HubProperties.fromEnvironment();
        // F-15: one holder, shared for this process's life, between HubProvisioningRunner (inside
        // the embedded Spring context DefaultHubOrchestrator boots) and HubControlServer (outside
        // it) — see FirstRunCredentialHolder's javadoc.
        FirstRunCredentialHolder credentialHolder = new FirstRunCredentialHolder();
        DefaultHubOrchestrator orchestrator = new DefaultHubOrchestrator(properties, credentialHolder);

        // Backup lives next to hub-state.json (%ProgramData%\EmberHub in a packaged install); the
        // "Esta máquina" destination is the backups\ folder the Tauri shell already creates there.
        Path stateFile = properties.stateFile().toAbsolutePath();
        HubBackupService backupService = new HubBackupService(
                properties, orchestrator,
                new BackupConfigStore(stateFile.resolveSibling("hub-backup.json"), stateFile.resolveSibling("backups")),
                new PostgresTools(properties.postgresBinDir(), properties.postgresPort()),
                HubVersion::current, Clock.systemDefaultZone());
        BackupScheduler backupScheduler = new BackupScheduler(backupService::runScheduledIfDue);
        backupScheduler.start();

        HubControlServer controlServer = new HubControlServer(orchestrator, backupService, credentialHolder);
        int port = controlServer.start();
        System.out.println("PORT=" + port);
        System.out.flush();

        orchestrator.start(args);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            backupScheduler.stop();
            orchestrator.stop();
            controlServer.stop();
        }, "ember-hub-shutdown"));
    }

    /**
     * Reads the profile straight from the environment, not from Spring — Spring hasn't started
     * yet. This is deliberately the ONLY place that matters: Spring's own DataSource
     * autoconfiguration connects to Postgres during context refresh, which runs before any
     * {@code ApplicationRunner} — too late to start portable Postgres from inside the Spring
     * lifecycle.
     */
    private static boolean isHubProfile() {
        String profiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profiles == null) {
            profiles = System.getProperty("spring.profiles.active", "");
        }
        return Arrays.asList(profiles.split(",")).contains("hub");
    }
}
