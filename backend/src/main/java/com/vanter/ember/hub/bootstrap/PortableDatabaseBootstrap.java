package com.vanter.ember.hub.bootstrap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Starts the portable, unpackaged Postgres binaries (see spec §2.3) before Spring's own
 * DataSource ever tries to connect. Covers the three boot-error scenarios spec §4 calls out:
 * port already in use, an empty data directory (first run — runs {@code initdb}), and a data
 * directory {@code pg_ctl} refuses to start (corruption or any other reason) — each gets a
 * distinct, actionable error message rather than a generic stack trace.
 */
public class PortableDatabaseBootstrap {

    private final Path dataDir;
    private final Path postgresBinDir;
    private final int port;

    public PortableDatabaseBootstrap(Path dataDir, Path postgresBinDir, int port) {
        this.dataDir = dataDir;
        this.postgresBinDir = postgresBinDir;
        this.port = port;
    }

    public void ensureRunning() throws PortableDatabaseException {
        if (isPortInUse(port)) {
            throw new PortableDatabaseException(
                    "El puerto " + port + " ya está en uso. Cierra la otra aplicación que lo está "
                            + "usando (o revisa si Ember Hub ya está corriendo) e intenta de nuevo.");
        }
        if (!Files.exists(dataDir.resolve("PG_VERSION"))) {
            initializeDataDirectory();
        }
        startServer();
        ensureApplicationDatabaseExists();
    }

    /** Stops the portable server gracefully; a no-op if it isn't running. */
    public void stop() throws PortableDatabaseException {
        if (!Files.exists(dataDir.resolve("postmaster.pid"))) {
            return;
        }
        Path pgCtlLogFile = dataDir.resolveSibling("pg_ctl-stop.log");
        try {
            Process process = new ProcessBuilder(
                    postgresBinDir.resolve("pg_ctl").toString(),
                    "stop",
                    "-D", dataDir.toString(),
                    "-m", "fast")
                    .redirectErrorStream(true)
                    .redirectOutput(pgCtlLogFile.toFile())
                    .start();
            process.waitFor();
        } catch (IOException e) {
            throw new PortableDatabaseException("Error deteniendo la base de datos local.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PortableDatabaseException("Error deteniendo la base de datos local.", e);
        }
    }

    boolean isPortInUse(int port) {
        try (ServerSocket ignored = new ServerSocket(port, 1, InetAddress.getByName("localhost"))) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private void initializeDataDirectory() throws PortableDatabaseException {
        Path pwFile = null;
        try {
            Files.createDirectories(dataDir);
            pwFile = Files.createTempFile("ember-hub-initdb", ".txt");
            Files.writeString(pwFile, "ember");

            Process process = new ProcessBuilder(
                    postgresBinDir.resolve("initdb").toString(),
                    "-D", dataDir.toString(),
                    "-U", "ember",
                    "--pwfile", pwFile.toString(),
                    "-E", "UTF8")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new PortableDatabaseException(
                        "No se pudo inicializar la base de datos local. Detalle:\n" + output);
            }
        } catch (IOException e) {
            throw new PortableDatabaseException("Error inicializando la base de datos local.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PortableDatabaseException("Error inicializando la base de datos local.", e);
        } finally {
            if (pwFile != null) {
                try {
                    Files.deleteIfExists(pwFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup of a temp file containing a throwaway local password.
                }
            }
        }
    }

    private void startServer() throws PortableDatabaseException {
        Path logFile = dataDir.resolveSibling("postgres.log");
        Path pgCtlLogFile = dataDir.resolveSibling("pg_ctl-start.log");
        try {
            Process process = new ProcessBuilder(
                    postgresBinDir.resolve("pg_ctl").toString(),
                    "start",
                    "-D", dataDir.toString(),
                    "-l", logFile.toString(),
                    "-o", "-p " + port,
                    "-w")
                    .redirectErrorStream(true)
                    .redirectOutput(pgCtlLogFile.toFile())
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new PortableDatabaseException(
                        "La base de datos local no pudo arrancar (posible corrupción de datos). "
                                + "Revisa el log en " + logFile + ":\n" + readLogTail(pgCtlLogFile) + "\n"
                                + readLogTail(logFile));
            }
        } catch (IOException e) {
            throw new PortableDatabaseException("Error arrancando la base de datos local.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PortableDatabaseException("Error arrancando la base de datos local.", e);
        }
    }

    /**
     * {@code initdb} only creates the {@code postgres}/{@code template0}/{@code template1}
     * databases, never one matching the app's own datasource URL — this creates {@code ember}
     * the first time, and is a no-op (via the "already exists" output) on every later boot.
     */
    private void ensureApplicationDatabaseExists() throws PortableDatabaseException {
        try {
            Process process = new ProcessBuilder(
                    postgresBinDir.resolve("createdb").toString(),
                    "-U", "ember",
                    "-h", "localhost",
                    "-p", String.valueOf(port),
                    "ember")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0 && !output.contains("already exists") && !output.contains("ya existe")) {
                throw new PortableDatabaseException(
                        "No se pudo crear la base de datos de la aplicación. Detalle:\n" + output);
            }
        } catch (IOException e) {
            throw new PortableDatabaseException("Error creando la base de datos de la aplicación.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PortableDatabaseException("Error creando la base de datos de la aplicación.", e);
        }
    }

    private String readLogTail(Path logFile) {
        try {
            List<String> lines = Files.readAllLines(logFile);
            int from = Math.max(0, lines.size() - 20);
            return String.join("\n", lines.subList(from, lines.size()));
        } catch (IOException e) {
            return "";
        }
    }
}
