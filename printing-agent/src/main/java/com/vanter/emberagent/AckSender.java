package com.vanter.emberagent;

import org.springframework.messaging.simp.stomp.StompSession;

public class AckSender {

    public void send(StompSession session, String jobId, String printerConfigId, String result, String error) {
        session.send("/app/print-agent/ack", new AckPayload(jobId, printerConfigId, result, error));
    }

    public record AckPayload(String jobId, String printerConfigId, String result, String error) {}
}
