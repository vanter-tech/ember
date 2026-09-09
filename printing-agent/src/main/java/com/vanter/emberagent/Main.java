package com.vanter.emberagent;

import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.credential.CredentialStores;
import com.vanter.emberagent.status.StatusHub;
import com.vanter.emberagent.ui.AgentDashboard;
import java.util.Arrays;

/**
 * Entry point. Default: run the {@link AgentRunner} loop on a background thread and open the Swing
 * {@link AgentDashboard} (add {@code --tray} to start minimized to the system tray). {@code
 * --headless} runs the loop only, no UI — the service-style / dev path.
 */
public class Main {

    public static void main(String[] args) {
        boolean headless = Arrays.asList(args).contains("--headless");
        boolean startInTray = Arrays.asList(args).contains("--tray");

        CredentialStore store = CredentialStores.forThisMachine();
        StatusHub status = new StatusHub();
        AgentRunner runner = new AgentRunner(store, status);

        Thread worker = new Thread(runner::runForever, "ember-agent-runner");
        worker.setDaemon(!headless);
        worker.start();

        if (headless) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        AgentDashboard.launch(status, store, runner, startInTray);
    }
}
