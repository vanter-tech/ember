package com.vanter.ember.printing.controller;

import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.service.BillReceiptPrintService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/printing/bills")
@RequiredArgsConstructor
public class BillReceiptController {

    private final BillReceiptPrintService billReceiptPrintService;

    public record PrintReceiptResponse(UUID jobId, PrintJobStatus status) {}

    @Operation(summary = "Print (or reprint) the receipt for a paid bill (WAITER/ADMIN)")
    @PostMapping("/{billId}/receipt")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public PrintReceiptResponse printReceipt(@PathVariable Long billId) {
        var job = billReceiptPrintService.enqueue(billId);
        return new PrintReceiptResponse(job.getId(), job.getStatus());
    }
}
