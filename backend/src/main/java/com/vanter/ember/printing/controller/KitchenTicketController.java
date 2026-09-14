package com.vanter.ember.printing.controller;

import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.service.KitchenTicketPrintService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/printing/kitchen-orders")
@RequiredArgsConstructor
public class KitchenTicketController {

    private final KitchenTicketPrintService kitchenTicketPrintService;

    public record PrintTicketResponse(UUID jobId, PrintJobStatus status) {}

    @Operation(summary = "Reprint the kitchen ticket for an order (KITCHEN)")
    @PostMapping("/{orderId}/ticket")
    @PreAuthorize("hasRole('KITCHEN')")
    public PrintTicketResponse printTicket(@PathVariable String orderId) {
        var job = kitchenTicketPrintService.enqueue(orderId);
        return new PrintTicketResponse(job.getId(), job.getStatus());
    }
}
