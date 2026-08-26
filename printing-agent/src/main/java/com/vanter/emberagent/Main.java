package com.vanter.emberagent;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.messaging.simp.stomp.StompSession;

public class Main {

    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("agent.properties");
        AgentConfig config = AgentConfig.load(configPath);

        AuthClient authClient = new AuthClient();
        PrinterConfigClient printerConfigClient = new PrinterConfigClient();
        AgentConnection agentConnection = new AgentConnection();
        PrintJobDispatcher dispatcher = new PrintJobDispatcher(
                new NetworkPrinterSender(), new UsbPrinterSender(), new WindowsPrintQueueSender());
        AckSender ackSender = new AckSender();

        while (true) {
            try {
                String jwt = authClient.fetchToken(config);
                String agentId = decodeAgentIdFromJwt(jwt);
                // Fetched once here only to fail fast (and log a count) if the config endpoint is
                // unreachable before opening the WS session — PrintJobHandler below refetches the
                // list fresh on every job, since a session can stay open for hours and printers can
                // change (added/edited/deactivated) at any point during that window.
                List<PrinterConfigClient.PrinterConfigDto> myPrinters =
                        printerConfigClient.fetchMyPrinters(config.backendBaseUrl(), jwt);
                PrintJobHandler jobHandler =
                        new PrintJobHandler(printerConfigClient, dispatcher, config.backendBaseUrl(), jwt);

                AtomicReference<StompSession> sessionRef = new AtomicReference<>();
                StompSession session = agentConnection.connect(
                        toWsUrl(config.backendBaseUrl()), jwt, agentId,
                        job -> jobHandler.handle(job, (jobId, printerConfigId, result, error) ->
                                ackSender.send(sessionRef.get(), jobId, printerConfigId, result, error)));
                sessionRef.set(session);
                System.out.println("[print-agent] conectado, agentId=" + agentId
                        + ", impresoras=" + myPrinters.size());

                // Block this thread while the STOMP session is alive; reconnect on any drop.
                while (session.isConnected()) {
                    TimeUnit.SECONDS.sleep(5);
                }
                System.err.println("[print-agent] sesion desconectada, reintentando...");
            } catch (Exception e) {
                System.err.println("[print-agent] conexion perdida, reintentando en 10s: " + e.getMessage());
                TimeUnit.SECONDS.sleep(10);
            }
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
