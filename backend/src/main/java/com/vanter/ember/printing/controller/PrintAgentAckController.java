package com.vanter.ember.printing.controller;

import com.vanter.ember.printing.dto.PrintJobAck;
import com.vanter.ember.printing.service.PrintDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PrintAgentAckController {

    private final PrintDispatchService printDispatchService;

    // WebSocketConfig.setApplicationDestinationPrefixes registers ("/app", "/app/print-agent")
    // — Spring strips the FIRST prefix that matches a destination, so the agent's
    // "/app/print-agent/ack" SEND resolves to mapped destination "/print-agent/ack", not "/ack".
    @MessageMapping("/print-agent/ack")
    public void ack(PrintJobAck ack) {
        printDispatchService.handleAck(ack);
    }
}
