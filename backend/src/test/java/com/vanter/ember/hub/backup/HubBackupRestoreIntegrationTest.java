package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.vanter.ember.hub.bootstrap.PortableDatabaseBootstrap;
import com.vanter.ember.hub.config.HubProperties;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the real bundled pg_dump/pg_restore; skipped when the vendor cache is absent. */
class HubBackupRestoreIntegrationTest {

    private static final Path PG_BIN =
            Path.of("../ember-hub/.vendor-cache/postgres/bin").toAbsolutePath().normalize();

    @TempDir Path tmp;
    int port;
    Path dataDir;
    Path minio;
    PortableDatabaseBootstrap db;
    FakeHubOrchestrator orchestrator = new FakeHubOrchestrator();
    HubBackupService service;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(Files.exists(PG_BIN.resolve("pg_dump.exe")) && Files.exists(PG_BIN.resolve("pg_restore.exe")),
                "portable Postgres not vendored");
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        dataDir = tmp.resolve("data/postgres");
        minio = Files.createDirectories(tmp.resolve("data/minio"));
        db = new PortableDatabaseBootstrap(dataDir, PG_BIN, port);
        db.ensureRunning();
        orchestrator.onStopAndWait = () -> {
            try {
                db.stop(); // what the real orchestrator's stopAndWait does to Postgres
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        HubProperties props = new HubProperties(dataDir, PG_BIN, tmp.resolve("license.key"), tmp.resolve("pub.der"),
                tmp.resolve("hub-state.json"), port, 8080, "", minio, tmp.resolve("minio-bin"), 9000);
        service = new HubBackupService(props, orchestrator,
                new BackupConfigStore(tmp.resolve("hub-backup.json"), tmp.resolve("backups")),
                new PostgresTools(PG_BIN, port), () -> "0.2.6.1", Clock.systemUTC());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) {
            db.stop();
        }
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + port + "/ember", "ember", "ember");
    }

    private String setting(String key) throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select v from settings_probe where k = '" + key + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    void restore_bringsBackDatabaseRowsSettingsAndMinioFiles() throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("create table settings_probe(k text primary key, v text)");
            st.execute("insert into settings_probe values ('tax', '15')");
        }
        Files.writeString(minio.resolve("logo.png"), "V1");

        BackupSnapshot snap = service.backupNow(null);
        assertThat(snap.status()).isEqualTo(BackupSnapshot.OK);

        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("update settings_probe set v = '99' where k = 'tax'");
            st.execute("insert into settings_probe values ('extra', 'x')");
        }
        Files.writeString(minio.resolve("logo.png"), "V2");
        Files.writeString(minio.resolve("extra.png"), "E");

        service.restore(snap.path(), false);

        assertThat(orchestrator.stopAndWaitCalls).isEqualTo(1);
        assertThat(orchestrator.startCalls).isEqualTo(1);
        db.ensureRunning(); // restore left Postgres stopped; the real orchestrator.start() does this
        assertThat(setting("tax")).isEqualTo("15");
        assertThat(setting("extra")).isNull();
        assertThat(Files.readString(minio.resolve("logo.png"))).isEqualTo("V1");
        assertThat(minio.resolve("extra.png")).doesNotExist();
        try (Stream<Path> s = Files.list(tmp.resolve("backups"))) {
            assertThat(s.map(p -> p.getFileName().toString()))
                    .anyMatch(n -> n.endsWith("-pre-restore.zip"));
        }
    }

    @Test
    void restore_withCorruptDataDir_movesItAsideAndStillRestores() throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("create table settings_probe(k text primary key, v text)");
            st.execute("insert into settings_probe values ('tax', '15')");
        }
        BackupSnapshot snap = service.backupNow(null);
        db.stop();
        Files.writeString(dataDir.resolve("PG_VERSION"), "99"); // pg_ctl refuses to start this cluster

        service.restore(snap.path(), true); // Postgres can't be dumped, so skip the safety snapshot

        db.ensureRunning();
        assertThat(setting("tax")).isEqualTo("15");
        try (Stream<Path> s = Files.list(dataDir.getParent())) {
            assertThat(s.map(p -> p.getFileName().toString())).anyMatch(n -> n.startsWith("postgres.corrupt-"));
        }
    }
}
