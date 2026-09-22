package com.vanter.ember.hub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * F-21: proves a fresh {@code initdb} actually honors a non-default password end to end (real
 * {@code initdb}/{@code pg_ctl}, real JDBC connection) — not just that the string got threaded
 * through the constructor. Skipped when the vendored Postgres binaries aren't present, same
 * pattern as {@link com.vanter.ember.hub.backup.HubBackupRestoreIntegrationTest}.
 */
class PortableDatabaseBootstrapCredentialsIntegrationTest {

    private static final Path PG_BIN =
            Path.of("../ember-hub/.vendor-cache/postgres/bin").toAbsolutePath().normalize();

    @TempDir Path tmp;
    private PortableDatabaseBootstrap db;
    private int port;

    private int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) {
            db.stop();
        }
    }

    @Test
    void aFreshInstallOnlyAcceptsItsOwnRandomPassword() throws Exception {
        assumeTrue(Files.exists(PG_BIN.resolve("initdb.exe")) && Files.exists(PG_BIN.resolve("pg_ctl.exe")),
                "portable Postgres not vendored");
        port = freePort();
        String randomPassword = "f21-" + java.util.UUID.randomUUID();
        db = new PortableDatabaseBootstrap(tmp.resolve("data"), PG_BIN, port, randomPassword);

        db.ensureRunning();

        // The random password this install was actually initialized with works.
        try (Connection ok = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:" + port + "/ember", "ember", randomPassword)) {
            assertThat(ok.isValid(5)).isTrue();
        }

        // F-21's regression case: the old hardcoded default must NOT still work.
        assertThatThrownBy(() -> DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:" + port + "/ember", "ember", "ember"))
                .isInstanceOf(SQLException.class);
    }
}
