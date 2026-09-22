package com.vanter.ember.hub.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin process wrapper over the {@code pg_dump}/{@code dropdb}/{@code createdb}/{@code pg_restore}
 * binaries the portable Postgres already ships. Same user/host/auth assumptions as
 * {@code PortableDatabaseBootstrap} (user {@code ember}, database {@code ember}, loopback,
 * {@code scram-sha-256} since F-21 — {@code PGPASSWORD} is set on every command's environment,
 * never passed as a CLI argument, which would leak it via the process list/log).
 * Non-final so tests can stub the process calls.
 *
 * <p>Every call is non-interactive ({@code -w}) and has a timeout: if the port is answered by some
 * other, password-protected Postgres (a dev Docker one) whose password we don't have, the tools
 * would otherwise prompt for a password on the console and never return, leaving the Hub's backup
 * button stuck.
 */
public class PostgresTools {

    private static final String USER = "ember";
    private static final String HOST = "127.0.0.1";
    private static final String DB = "ember";

    private static final Duration DUMP_TIMEOUT = Duration.ofMinutes(60);
    private static final Duration RESTORE_TIMEOUT = Duration.ofMinutes(120);
    private static final Duration ADMIN_TIMEOUT = Duration.ofMinutes(3);

    private final Path binDir;
    private final int port;
    private final String password;

    /** Uses the historical hardcoded local password (F-21's pre-fix default) — for callers that
     *  don't rotate credentials (most tests: they stub the process calls, never really connect). */
    public PostgresTools(Path binDir, int port) {
        this(binDir, port, "ember");
    }

    public PostgresTools(Path binDir, int port, String password) {
        this.binDir = binDir;
        this.port = port;
        this.password = password;
    }

    public void dump(Path outFile) throws IOException {
        run("pg_dump", DUMP_TIMEOUT, "-Fc", "-U", USER, "-h", HOST, "-p", String.valueOf(port),
                "-f", outFile.toString(), DB);
    }

    public void dropAndCreate() throws IOException {
        run("dropdb", ADMIN_TIMEOUT, "--force", "--if-exists", "-U", USER, "-h", HOST,
                "-p", String.valueOf(port), DB);
        run("createdb", ADMIN_TIMEOUT, "-U", USER, "-h", HOST, "-p", String.valueOf(port), DB);
    }

    public void restore(Path dumpFile) throws IOException {
        run("pg_restore", RESTORE_TIMEOUT, "--no-owner", "-U", USER, "-h", HOST, "-p", String.valueOf(port),
                "-d", DB, dumpFile.toString());
    }

    /** The executable followed by {@code -w} (never prompt for a password) and the given arguments. */
    List<String> buildCommand(String tool, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(binDir.resolve(tool).toString());
        cmd.add("-w");
        cmd.addAll(Arrays.asList(args));
        return cmd;
    }

    private void run(String tool, Duration timeout, String... args) throws IOException {
        execute(buildCommand(tool, args), tool, timeout);
    }

    /** Runs {@code command}; kills it and fails if it has not exited after {@code timeout}. */
    void execute(List<String> command, String tool, Duration timeout) throws IOException {
        Path log = Files.createTempFile("ember-hub-pg-", ".log");
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile());
            builder.environment().put("PGPASSWORD", password);
            Process process = builder.start();
            process.getOutputStream().close(); // nothing will ever be typed into it
            boolean finished;
            try {
                finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException(tool + " fue interrumpido.", e);
            }
            if (!finished) {
                process.destroyForcibly();
                throw new IOException(tool + " no terminó a tiempo y se canceló.");
            }
            if (process.exitValue() != 0) {
                throw new IOException(tool + " falló (código " + process.exitValue() + "): "
                        + new String(Files.readAllBytes(log)).trim());
            }
        } finally {
            try {
                Files.deleteIfExists(log);
            } catch (IOException ignored) {
                // a killed process may still hold the log open; it is a temp file
            }
        }
    }
}
