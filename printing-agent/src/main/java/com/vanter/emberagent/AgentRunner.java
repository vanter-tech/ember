package com.vanter.emberagent;

import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.status.StatusHub;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.messaging.simp.stomp.StompSession;

/**
 * The headless core loop, extracted verbatim in behavior from the old {@code Main.main} (reconnect
 * with a 5s alive-poll / 10s error backoff, printers refetched per job by {@link PrintJobHandler}).
 * T4 additions, all at clearly marked points:
 * <ul>
 *   <li>config comes from {@link AgentConfig#resolve} (credential store → {@code agent.properties}),
 *       and an empty result parks the loop in {@link StatusHub.Phase#UNPAIRED}, re-checking every
 *       few seconds so pairing from the dashboard resumes it with no restart;
 *   <li>on each successful connect the local Windows print queues are enumerated and reported to
 *       the backend (best-effort — never blocks the session);
 *   <li>phase / connected / job state is pushed into the {@link StatusHub} the desktop UI observes.
 * </ul>
 */
public class AgentRunner {

    private final CredentialStore store;
    private final StatusHub status;
    private final Path propertiesFile;

    private final AuthClient authClient = new AuthClient();
    private final PrinterConfigClient printerConfigClient = new PrinterConfigClient();
    private final AgentConnection agentConnection = new AgentConnection();
    private final AckSender ackSender = new AckSender();
    private final WindowsPrinterEnumerator enumerator = new WindowsPrinterEnumerator();
    private final DiscoveredPrintersClient discoveredPrintersClient = new DiscoveredPrintersClient();
    private final PrintJobDispatcher dispatcher = new PrintJobDispatcher(
            new NetworkPrinterSender(), new UsbPrinterSender(), new WindowsPrintQueueSender());

    private final AtomicReference<StompSession> currentSession = new AtomicReference<>();
    private volatile boolean running = true;

    public AgentRunner(CredentialStore store, StatusHub status) {
        this(store, status, Path.of("agent.properties"));
    }

    AgentRunner(CredentialStore store, StatusHub status, Path propertiesFile) {
        this.store = store;
        this.status = status;
        this.propertiesFile = propertiesFile;
    }

    public void stop() {
        running = false;
        disconnectCurrent();
    }

    /** Drops the live STOMP session (if any) so the loop reconnects on its next pass. */
    public void requestReconnect() {
        disconnectCurrent();
    }

    public void runForever() {
        while (running) {
            Optional<AgentConfig> maybeConfig = AgentConfig.resolve(store, propertiesFile);
            if (maybeConfig.isEmpty()) {
                status.setPhase(StatusHub.Phase.UNPAIRED, "Sin emparejar");
                sleep(3);
                continue;
            }
            AgentConfig config = maybeConfig.get();
            try {
                status.setPhase(StatusHub.Phase.CONNECTING, "Conectando…");
                String jwt = authClient.fetchToken(config);
                String agentId = decodeAgentIdFromJwt(jwt);

                reportDiscoveredPrinters(config, jwt);

                // Fetched once here only to fail fast (and show a count) if the config endpoint is
                // unreachable before opening the WS session — PrintJobHandler refetches the list
                // fresh on every job.
                List<PrinterConfigClient.PrinterConfigDto> myPrinters =
                        printerConfigClient.fetchMyPrinters(config.backendBaseUrl(), jwt);
                PrintJobHandler jobHandler = new PrintJobHandler(
                        printerConfigClient, dispatcher, config.backendBaseUrl(), jwt, status);

                AtomicReference<StompSession> sessionRef = new AtomicReference<>();
                StompSession session = agentConnection.connect(
                        toWsUrl(config.backendBaseUrl()), jwt, agentId,
                        job -> jobHandler.handle(job, (jobId, printerConfigId, result, error) ->
                                ackSender.send(sessionRef.get(), jobId, printerConfigId, result, error)));
                sessionRef.set(session);
                currentSession.set(session);
                status.setConnected(agentId, myPrinters.size());
                System.out.println("[print-agent] conectado, agentId=" + agentId
                        + ", impresoras=" + myPrinters.size());

                while (running && session.isConnected()) {
                    TimeUnit.SECONDS.sleep(5);
                }
                currentSession.set(null);
                if (running) {
                    status.setPhase(StatusHub.Phase.RETRYING, "Sesión desconectada, reintentando…");
                }
            } catch (Exception e) {
                currentSession.set(null);
                System.err.println("[print-agent] conexion perdida, reintentando en 10s: " + e.getMessage());
                status.setPhase(StatusHub.Phase.RETRYING, "Conexión perdida, reintentando en 10s");
                sleep(10);
            }
        }
    }

    private void reportDiscoveredPrinters(AgentConfig config, String jwt) {
        try {
            discoveredPrintersClient.report(config.backendBaseUrl(), jwt, enumerator.enumerate());
        } catch (RuntimeException e) {
            // best-effort; never blocks the connection
        }
    }

    private void disconnectCurrent() {
        StompSession session = currentSession.getAndSet(null);
        if (session != null) {
            try {
                session.disconnect();
            } catch (RuntimeException ignored) {
                // already dead
            }
        }
    }

    private static void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String decodeAgentIdFromJwt(String jwt) {
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
        return payloadJson.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
    }

    private static String toWsUrl(String httpBaseUrl) {
        return httpBaseUrl.replaceFirst("^http", "ws");
    }
}
