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
        NetworkPrinterSender networkPrinterSender = new NetworkPrinterSender();
        UsbPrinterSender usbPrinterSender = new UsbPrinterSender();
        AckSender ackSender = new AckSender();

        while (true) {
            try {
                String jwt = authClient.fetchToken(config);
                String agentId = decodeAgentIdFromJwt(jwt);
                List<PrinterConfigClient.PrinterConfigDto> myPrinters =
                        printerConfigClient.fetchMyPrinters(config.backendBaseUrl(), jwt);

                AtomicReference<StompSession> sessionRef = new AtomicReference<>();
                StompSession session = agentConnection.connect(
                        toWsUrl(config.backendBaseUrl()), jwt, agentId, job -> {
                            for (PrinterConfigClient.PrinterConfigDto printer : myPrinters) {
                                if (!printer.role().equals(job.role())) continue;
                                try {
                                    if ("NETWORK".equals(printer.connectionType())) {
                                        networkPrinterSender.print(printer, job.payload());
                                    } else {
                                        usbPrinterSender.print(printer, job.payload());
                                    }
                                    ackSender.send(sessionRef.get(), job.jobId(), printer.id(), "PRINTED", null);
                                } catch (Exception e) {
                                    ackSender.send(sessionRef.get(), job.jobId(), printer.id(), "ERROR", e.getMessage());
                                }
                            }
                        });
                sessionRef.set(session);

                // Block this thread while the STOMP session is alive; reconnect on any drop.
                while (session.isConnected()) {
                    TimeUnit.SECONDS.sleep(5);
                }
            } catch (Exception e) {
                System.err.println("Agent connection lost, retrying in 10s: " + e.getMessage());
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
