package com.vanter.ember.hub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * F-21: proves a fresh MinIO data directory honors a non-default {@code MINIO_ROOT_PASSWORD}
 * (real {@code minio} binary, real S3 auth call), and — unlike Postgres — that restarting the
 * *same* data directory under a *different* secret still works with no migration step, which is
 * exactly the claim {@link PortableMinioBootstrap}'s javadoc makes. Skipped when the vendored
 * MinIO binary isn't present.
 */
class PortableMinioBootstrapCredentialsIntegrationTest {

    private static final Path MINIO_BIN =
            Path.of("../ember-hub/.vendor-cache/minio").toAbsolutePath().normalize();

    @TempDir Path tmp;
    private PortableMinioBootstrap minio;

    private int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private MinioClient client(int port, String secretKey) {
        return MinioClient.builder()
                .endpoint("http://127.0.0.1:" + port)
                .credentials("ember-hub", secretKey)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (minio != null) {
            minio.stop();
        }
    }

    @Test
    void aFreshInstallOnlyAcceptsItsOwnRandomSecretKey() throws Exception {
        assumeTrue(Files.exists(MINIO_BIN.resolve("minio.exe")), "portable MinIO not vendored");
        int port = freePort();
        Path dataDir = tmp.resolve("data-a");
        String secretA = "f21-minio-" + java.util.UUID.randomUUID();
        minio = new PortableMinioBootstrap(dataDir, MINIO_BIN, port, secretA);

        minio.ensureRunning();

        assertThat(client(port, secretA)
                .bucketExists(BucketExistsArgs.builder().bucket("no-such-bucket").build()))
                .isFalse(); // no exception thrown = authenticated successfully

        assertThatThrownBy(() -> client(port, "ember-hub-local")
                .bucketExists(BucketExistsArgs.builder().bucket("no-such-bucket").build()))
                .isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void restartingTheSameDataDirectoryUnderADifferentSecretNeedsNoMigration() throws Exception {
        assumeTrue(Files.exists(MINIO_BIN.resolve("minio.exe")), "portable MinIO not vendored");
        int port = freePort();
        Path dataDir = tmp.resolve("data-b");

        minio = new PortableMinioBootstrap(dataDir, MINIO_BIN, port, "first-secret-1234");
        minio.ensureRunning();
        minio.stop();

        int port2 = freePort();
        minio = new PortableMinioBootstrap(dataDir, MINIO_BIN, port2, "second-secret-5678");
        minio.ensureRunning();

        assertThat(client(port2, "second-secret-5678")
                .bucketExists(BucketExistsArgs.builder().bucket("no-such-bucket").build()))
                .isFalse();
    }
}
