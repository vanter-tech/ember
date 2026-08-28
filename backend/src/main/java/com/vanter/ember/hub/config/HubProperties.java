package com.vanter.ember.hub.config;

import java.nio.file.Path;

/**
 * Deliberately NOT a Spring {@code @ConfigurationProperties} bean: {@link
 * com.vanter.ember.EmberApplication#main} needs these values before {@code SpringApplication.run}
 * is even called (to start portable Postgres before Spring's own DataSource autoconfiguration
 * tries to connect), so this has to be constructible with zero Spring context. The same instance
 * shape is reused as a {@code @Bean} later (see HubBeansConfig) for post-boot consumers.
 */
public record HubProperties(
        Path dataDir,
        Path postgresBinDir,
        Path licenseFile,
        Path publicKeyFile,
        Path stateFile,
        int postgresPort,
        int serverPort,
        String activationUrl,
        Path minioDataDir,
        Path minioBinDir,
        int minioPort,
        String heartbeatUrl,
        int suspendedGraceHours) {

    /** Back-compat: the pre-heartbeat 11-arg shape, still used by tests that don't care about it. */
    public HubProperties(
            Path dataDir, Path postgresBinDir, Path licenseFile, Path publicKeyFile, Path stateFile,
            int postgresPort, int serverPort, String activationUrl,
            Path minioDataDir, Path minioBinDir, int minioPort) {
        this(dataDir, postgresBinDir, licenseFile, publicKeyFile, stateFile, postgresPort, serverPort,
                activationUrl, minioDataDir, minioBinDir, minioPort, "", 48);
    }

    public static HubProperties fromEnvironment() {
        return new HubProperties(
                Path.of(env("EMBER_HUB_DATA_DIR", "./data/postgres")),
                Path.of(env("EMBER_HUB_POSTGRES_BIN_DIR", "./postgres/bin")),
                Path.of(env("EMBER_HUB_LICENSE_FILE", "./license.key")),
                Path.of(env("EMBER_HUB_PUBLIC_KEY_FILE", "./hub-public-key.der")),
                Path.of(env("EMBER_HUB_STATE_FILE", "./hub-state.json")),
                Integer.parseInt(env("EMBER_HUB_POSTGRES_PORT", "5432")),
                Integer.parseInt(env("EMBER_HUB_SERVER_PORT", "8080")),
                env("EMBER_HUB_ACTIVATION_URL", ""),
                Path.of(env("EMBER_HUB_MINIO_DATA_DIR", "./data/minio")),
                Path.of(env("EMBER_HUB_MINIO_BIN_DIR", "./minio/bin")),
                Integer.parseInt(env("EMBER_HUB_MINIO_PORT", "9000")),
                env("EMBER_HUB_HEARTBEAT_URL", ""),
                Integer.parseInt(env("EMBER_HUB_SUSPENDED_GRACE_HOURS", "48")));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null ? value : fallback;
    }
}
