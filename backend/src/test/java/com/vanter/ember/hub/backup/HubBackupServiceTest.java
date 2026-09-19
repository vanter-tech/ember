package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.control.ServicePhase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HubBackupServiceTest {

    static final class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TempDir Path tmp;
    MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
    FakeHubOrchestrator orchestrator = new FakeHubOrchestrator();
    BackupConfigStore store;
    HubBackupService service;
    Path minio;
    boolean dumpFails;
    int dumpCalls;
    String[] version = {"0.2.6.1"};

    @BeforeEach
    void setUp() throws IOException {
        minio = Files.createDirectories(tmp.resolve("minio"));
        Files.writeString(minio.resolve("a.txt"), "A");
        store = new BackupConfigStore(tmp.resolve("hub-backup.json"), tmp.resolve("backups"));
        PostgresTools tools = new PostgresTools(tmp.resolve("no-bin"), 1) {
            @Override
            public void dump(Path out) throws IOException {
                dumpCalls++;
                if (dumpFails) {
                    throw new IOException("sin conexión");
                }
                Files.writeString(out, "DUMP");
            }
        };
        HubProperties props = new HubProperties(tmp.resolve("pg-data"), tmp.resolve("no-bin"),
                tmp.resolve("license.key"), tmp.resolve("pub.der"), tmp.resolve("hub-state.json"),
                5432, 8080, "", minio, tmp.resolve("minio-bin"), 9000);
        service = new HubBackupService(props, orchestrator, store, tools, () -> version[0], clock);
    }

    @Test
    void backupNow_writesZipWithManifestAndRecordsRun() throws Exception {
        BackupSnapshot snap = service.backupNow(null);

        assertThat(snap.status()).isEqualTo(BackupSnapshot.OK);
        assertThat(snap.id()).isEqualTo("ember-backup-2026-09-19T10-00-00.zip");
        assertThat(Path.of(snap.path())).exists();
        assertThat(BackupArchive.readManifest(Path.of(snap.path())).appVersion()).isEqualTo("0.2.6.1");
        assertThat(store.loadLastRun()).isEqualTo(snap);
        assertThat(service.status().nextScheduledRun()).isEqualTo("2026-09-20T10:00:00Z");
        assertThat(service.status().defaultDestDir()).isEqualTo(tmp.resolve("backups").toString());
    }

    @Test
    void backupNow_customDestDir_usesIt() {
        Path usb = tmp.resolve("usb");

        BackupSnapshot snap = service.backupNow(usb.toString());

        assertThat(Path.of(snap.path()).getParent()).isEqualTo(usb);
    }

    @Test
    void backupNow_unwritableDest_returnsErrorAndNeverThrows() throws Exception {
        Path file = Files.writeString(tmp.resolve("afile"), "x");

        BackupSnapshot snap = service.backupNow(file.resolve("sub").toString());

        assertThat(snap.status()).isEqualTo(BackupSnapshot.ERROR);
        assertThat(snap.errorMessage()).contains("No se pudo escribir");
        assertThat(store.loadLastRun().status()).isEqualTo(BackupSnapshot.ERROR);
        assertThat(store.loadLastSuccessAt()).isNull();
    }

    @Test
    void backupNow_dumpFails_returnsErrorAndLeavesNoFiles() throws Exception {
        dumpFails = true;

        BackupSnapshot snap = service.backupNow(null);

        assertThat(snap.status()).isEqualTo(BackupSnapshot.ERROR);
        assertThat(snap.errorMessage()).contains("sin conexión");
        try (Stream<Path> s = Files.list(tmp.resolve("backups"))) {
            assertThat(s.count()).isZero();
        }
    }

    @Test
    void scheduledRun_prunesBeyondRetention_keepingPreRestoreSnapshots() throws Exception {
        Path dest = Files.createDirectories(tmp.resolve("backups"));
        store.saveConfig(new BackupConfig(dest.toString(), 2));
        for (String d : List.of("11", "12", "13", "14")) {
            Files.writeString(dest.resolve("ember-backup-2026-09-" + d + "T10-00-00.zip"), "old");
        }
        Files.writeString(dest.resolve("ember-backup-2026-09-01T10-00-00-pre-restore.zip"), "safe");

        service.runBackup(null, true);

        assertThat(dest.resolve("ember-backup-2026-09-19T10-00-00.zip")).exists();
        assertThat(dest.resolve("ember-backup-2026-09-14T10-00-00.zip")).exists();
        assertThat(dest.resolve("ember-backup-2026-09-13T10-00-00.zip")).doesNotExist();
        assertThat(dest.resolve("ember-backup-2026-09-11T10-00-00.zip")).doesNotExist();
        assertThat(dest.resolve("ember-backup-2026-09-01T10-00-00-pre-restore.zip")).exists();
    }

    @Test
    void manualBackup_neverPrunes() throws Exception {
        Path dest = Files.createDirectories(tmp.resolve("backups"));
        store.saveConfig(new BackupConfig(dest.toString(), 1));
        Files.writeString(dest.resolve("ember-backup-2026-09-11T10-00-00.zip"), "old");

        service.backupNow(null);

        assertThat(dest.resolve("ember-backup-2026-09-11T10-00-00.zip")).exists();
    }

    @Test
    void listSnapshots_newestFirst_andFlagsBrokenFile() throws Exception {
        service.backupNow(null);
        clock.advance(Duration.ofDays(1));
        service.backupNow(null);
        Files.writeString(tmp.resolve("backups/ember-backup-2026-01-01T00-00-00.zip"), "garbage");

        List<BackupSnapshot> list = service.listSnapshots();

        assertThat(list).extracting(BackupSnapshot::id).containsExactly(
                "ember-backup-2026-09-20T10-00-00.zip",
                "ember-backup-2026-09-19T10-00-00.zip",
                "ember-backup-2026-01-01T00-00-00.zip");
        assertThat(list.get(2).status()).isEqualTo(BackupSnapshot.ERROR);
    }

    @Test
    void inspect_validFile_returnsSnapshot() throws Exception {
        BackupSnapshot made = service.backupNow(null);

        BackupSnapshot inspected = service.inspect(made.path());

        assertThat(inspected.appVersion()).isEqualTo("0.2.6.1");
        assertThat(inspected.createdAt()).isEqualTo(made.createdAt());
    }

    @Test
    void inspect_notABackup_throwsInvalid() throws Exception {
        Path junk = Files.writeString(tmp.resolve("junk.zip"), "hola");

        assertThatThrownBy(() -> service.inspect(junk.toString()))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.code()).isEqualTo(BackupException.INVALID));
    }

    @Test
    void inspect_newerThanRunningHub_throwsIncompatible() {
        version[0] = "0.9.0";
        BackupSnapshot made = service.backupNow(null);
        version[0] = "0.2.6.1";

        assertThatThrownBy(() -> service.inspect(made.path()))
                .isInstanceOfSatisfying(BackupException.class, e -> {
                    assertThat(e.code()).isEqualTo(BackupException.INCOMPATIBLE);
                    assertThat(e.getMessage()).contains("0.9.0");
                });
    }

    @Test
    void scheduledIfDue_skipsWhenPostgresNotRunning() {
        orchestrator.postgres = ServicePhase.STOPPED;

        service.runScheduledIfDue();

        assertThat(dumpCalls).isZero();
    }

    @Test
    void scheduledIfDue_runsOnceThenWaits24hSinceLastSuccess() {
        service.runScheduledIfDue();
        clock.advance(Duration.ofHours(1));
        service.runScheduledIfDue();
        assertThat(dumpCalls).isEqualTo(1);

        clock.advance(Duration.ofHours(24));
        service.runScheduledIfDue();
        assertThat(dumpCalls).isEqualTo(2);
    }

    @Test
    void scheduledIfDue_afterFailure_backsOffOneHour() {
        dumpFails = true;
        service.runScheduledIfDue();
        clock.advance(Duration.ofMinutes(30));
        service.runScheduledIfDue();
        assertThat(dumpCalls).isEqualTo(1);

        clock.advance(Duration.ofMinutes(31));
        service.runScheduledIfDue();
        assertThat(dumpCalls).isEqualTo(2);
    }

    @Test
    void setConfig_rejectsInvalidValues() {
        assertThatThrownBy(() -> service.setConfig(new BackupConfig(" ", 7)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setConfig(new BackupConfig("E:\\x", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.setConfig(new BackupConfig("E:\\x", 3))).isEqualTo(new BackupConfig("E:\\x", 3));
    }
}
