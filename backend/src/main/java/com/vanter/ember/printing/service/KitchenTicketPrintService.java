package com.vanter.ember.printing.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintJobRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Enqueues a {@code KITCHEN_TICKET} print job on demand — the KDS's manual "reprint" action (a
 * paper-jam backup), independent of {@code hardware.autoPrintTickets} (that setting only gates
 * the automatic ticket fired on order confirmation; a manual reprint should always be possible).
 */
@Service
@RequiredArgsConstructor
public class KitchenTicketPrintService {

    private final KitchenOrderRepository kitchenOrderRepository;
    private final PrintJobRepository printJobRepository;
    private final PrintDispatchService printDispatchService;

    public PrintJob enqueue(String orderId) {
        KitchenOrder order = kitchenOrderRepository
                .findByIdAndTenantId(orderId, TenantContextHolder.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen order not found: " + orderId));

        LocalDateTime now = LocalDateTime.now();
        PrintJob job = PrintJob.builder()
                .id(UUID.randomUUID())
                .role(PrinterRole.KITCHEN)
                .sourceType(PrintJobSourceType.KITCHEN_TICKET)
                .sourceId(order.getSessionId())
                .payload(renderTicketPayload(order))
                .status(PrintJobStatus.PENDING)
                .attempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        // saveAndFlush, not save — same reasoning as PrintingEventListener/BillReceiptPrintService:
        // PrintJob.tenantId is a Hibernate @TenantId field generated at flush time, and
        // dispatch() reads it immediately.
        printJobRepository.saveAndFlush(job);
        printDispatchService.dispatch(job);
        return job;
    }

    private String renderTicketPayload(KitchenOrder order) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mesa ").append(order.getTableNumber()).append('\n');
        for (KitchenItem item : order.getItems()) {
            sb.append("- ").append(item.getName()).append('\n');
            for (String modifier : item.getModifiers()) {
                sb.append("  · ").append(modifier).append('\n');
            }
        }
        return sb.toString();
    }
}
