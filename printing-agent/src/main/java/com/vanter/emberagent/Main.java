package com.vanter.emberagent;

import com.vanter.emberagent.control.LocalControlServer;
import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.credential.CredentialStores;
import com.vanter.emberagent.status.StatusHub;
import java.io.IOException;

/**
 * Entry point. Runs headless: the {@link AgentRunner} loop on a background thread plus a
 * {@link LocalControlServer} that a separate Tauri shell process polls/calls instead of the old
 * Swing dashboard reading {@link StatusHub} in-process (spec
 * docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md). Prints "PORT=&lt;n&gt;" to
 * stdout once the control server is listening — the Tauri shell reads that single line from this
 * process's stdout to know where to send requests. No more --tray/--headless flags: this process
 * never owns a window.
 */
public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        CredentialStore store = CredentialStores.forThisMachine();
        StatusHub status = new StatusHub();
        AgentRunner runner = new AgentRunner(store, status);

        Thread worker = new Thread(runner::runForever, "ember-agent-runner");
        worker.setDaemon(false);
        worker.start();

        LocalControlServer controlServer = new LocalControlServer(status, store, runner);
        int port = controlServer.start();
        System.out.println("PORT=" + port);
        System.out.flush();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            runner.stop();
            controlServer.stop();
        }, "ember-agent-shutdown"));

        worker.join();
    }
}
