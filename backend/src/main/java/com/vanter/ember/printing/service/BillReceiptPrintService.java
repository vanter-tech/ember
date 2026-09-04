package com.vanter.ember.printing.service;

import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.printing.exception.BillNotPaidException;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintJobRepository;
import com.vanter.ember.settings.service.SettingService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Enqueues a {@code BILL_RECEIPT} print job on demand (WAITER/ADMIN reprint), gated on {@code
 * Bill.status == PAID}. Renders through the same {@link ReceiptRenderer} the automatic {@code
 * PaymentCompleted} path uses, so the on-demand copy is byte-for-byte identical.
 */
@Service
@RequiredArgsConstructor
public class BillReceiptPrintService {

    private final BillRepository billRepository;
    private final SettingService settingService;
    private final ReceiptRenderer receiptRenderer;
    private final PrintJobRepository printJobRepository;
    private final PrintDispatchService printDispatchService;

    public PrintJob enqueue(Long billId) {
        var bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));
        if (bill.getStatus() != BillStatus.PAID) {
            throw new BillNotPaidException(billId);
        }

        UUID tenantId = TenantContextHolder.requireTenantId();
        String payload = receiptRenderer.render(
                billId, settingService.getSettings(tenantId).getPayload());

        LocalDateTime now = LocalDateTime.now();
        PrintJob job = PrintJob.builder()
                .id(UUID.randomUUID())
                .role(PrinterRole.RECEIPT)
                .sourceType(PrintJobSourceType.BILL_RECEIPT)
                .sourceId(String.valueOf(billId))
                .payload(payload)
                .status(PrintJobStatus.PENDING)
                .attempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        // saveAndFlush, not save: PrintJob.tenantId is a Hibernate @TenantId field generated at
        // flush time, and dispatch() reads it immediately — same reasoning as PrintingEventListener.
        printJobRepository.saveAndFlush(job);
        printDispatchService.dispatch(job);
        return job;
    }
}
