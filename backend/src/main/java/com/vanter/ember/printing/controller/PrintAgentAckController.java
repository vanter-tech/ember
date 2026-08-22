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

    @MessageMapping("/ack")
    public void ack(PrintJobAck ack) {
        printDispatchService.handleAck(ack);
    }
}
