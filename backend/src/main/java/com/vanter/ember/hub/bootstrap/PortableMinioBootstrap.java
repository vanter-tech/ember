package com.vanter.ember.hub.bootstrap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Starts the portable, unpackaged MinIO binary (spec §2.2/gap found 2026-08-25 in PROGRESS.md)
 * before Spring's own {@code MinioClient} bean ever tries to connect — same lifecycle shape as
 * {@link PortableDatabaseBootstrap}, adapted to MinIO's differences: no blocking "wait until
 * ready" start flag (polls the health endpoint instead) and no on-disk pid file (the live
 * {@link Process} handle is tracked in memory instead).
 */
public class PortableMinioBootstrap {

    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration HEALTH_POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration HEALTH_REQUEST_TIMEOUT = Duration.ofSeconds(1);

    private final Path dataDir;
    private final Path minioBinDir;
    private final int port;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private Process process;

    public PortableMinioBootstrap(Path dataDir, Path minioBinDir, int port) {
        this.dataDir = dataDir;
        this.minioBinDir = minioBinDir;
        this.port = port;
    }

    public void ensureRunning() throws PortableMinioException {
        if (isPortInUse(port)) {
            throw new PortableMinioException(
                    "El puerto " + port + " ya está en uso. Cierra la otra aplicación que lo está "
                            + "usando (o revisa si Ember Hub ya está corriendo) e intenta de nuevo.");
        }
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new PortableMinioException("No se pudo crear la carpeta de datos de MinIO.", e);
        }
        startServer();
        waitUntilHealthy();
    }

    /** Stops the portable server gracefully; a no-op if it isn't running. */
    public void stop() throws PortableMinioException {
        if (process == null || !process.isAlive()) {
            process = null;
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PortableMinioException("Error deteniendo MinIO local.", e);
        } finally {
            process = null;
        }
    }

    boolean isPortInUse(int port) {
        try (ServerSocket ignored = new ServerSocket(port, 1, InetAddress.getByName("localhost"))) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/minio/health/live"))
                    .timeout(HEALTH_REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private void startServer() throws PortableMinioException {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    minioBinDir.resolve("minio").toString(),
                    "server", dataDir.toString(),
                    "--address", "127.0.0.1:" + port)
                    .redirectErrorStream(true)
                    .redirectOutput(dataDir.resolveSibling("minio.log").toFile());
            builder.environment().put("MINIO_ROOT_USER", "ember-hub");
            builder.environment().put("MINIO_ROOT_PASSWORD", "ember-hub-local");
            builder.environment().put("MINIO_BROWSER", "off");
            process = builder.start();
        } catch (IOException e) {
            throw new PortableMinioException("Error arrancando MinIO local.", e);
        }
    }

    private void waitUntilHealthy() throws PortableMinioException {
        long deadline = System.currentTimeMillis() + HEALTH_CHECK_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (isHealthy()) {
                return;
            }
            if (!process.isAlive()) {
                throw new PortableMinioException(
                        "MinIO local no pudo arrancar (terminó antes de estar listo). Revisa el log en "
                                + dataDir.resolveSibling("minio.log"));
            }
            try {
                Thread.sleep(HEALTH_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PortableMinioException("Error esperando a que MinIO local arranque.", e);
            }
        }
        throw new PortableMinioException(
                "MinIO local no respondió a tiempo (" + HEALTH_CHECK_TIMEOUT.getSeconds() + "s). Revisa el "
                        + "log en " + dataDir.resolveSibling("minio.log"));
    }
}
