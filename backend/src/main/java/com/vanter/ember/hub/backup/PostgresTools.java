package com.vanter.ember.hub.backup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Thin process wrapper over the {@code pg_dump}/{@code dropdb}/{@code createdb}/{@code pg_restore}
 * binaries the portable Postgres already ships. Same user/host/auth assumptions as
 * {@code PortableDatabaseBootstrap} (user {@code ember}, database {@code ember}, loopback, trust).
 * Non-final so tests can stub the process calls.
 */
public class PostgresTools {

    private static final String USER = "ember";
    private static final String HOST = "127.0.0.1";
    private static final String DB = "ember";

    private final Path binDir;
    private final int port;

    public PostgresTools(Path binDir, int port) {
        this.binDir = binDir;
        this.port = port;
    }

    public void dump(Path outFile) throws IOException {
        run("pg_dump", "-Fc", "-U", USER, "-h", HOST, "-p", String.valueOf(port),
                "-f", outFile.toString(), DB);
    }

    public void dropAndCreate() throws IOException {
        run("dropdb", "--force", "--if-exists", "-U", USER, "-h", HOST, "-p", String.valueOf(port), DB);
        run("createdb", "-U", USER, "-h", HOST, "-p", String.valueOf(port), DB);
    }

    public void restore(Path dumpFile) throws IOException {
        run("pg_restore", "--no-owner", "-U", USER, "-h", HOST, "-p", String.valueOf(port),
                "-d", DB, dumpFile.toString());
    }

    private void run(String tool, String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(binDir.resolve(tool).toString());
        cmd.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit;
        try {
            exit = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(tool + " fue interrumpido.", e);
        }
        if (exit != 0) {
            throw new IOException(tool + " falló (código " + exit + "): " + output.trim());
        }
    }
}
