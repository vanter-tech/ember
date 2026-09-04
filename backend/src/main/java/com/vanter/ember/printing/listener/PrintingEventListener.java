package com.vanter.ember.printing.listener;

import com.vanter.ember.billing.event.PaymentCompleted;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintJobRepository;
import com.vanter.ember.printing.service.PrintDispatchService;
import com.vanter.ember.printing.service.ReceiptRenderer;
import com.vanter.ember.session.event.KitchenItemsConfirmed;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link PrintJob} from EXISTING domain events — {@link PaymentCompleted} and {@link
 * KitchenItemsConfirmed} — without touching their publishers (spec §3.3). Gate: kitchen
 * tickets print whenever {@code hardware.autoPrintTickets} is true; the customer receipt
 * additionally requires {@code hardware.printCustomerReceipt}.
 */
@Component
@RequiredArgsConstructor
public class PrintingEventListener {

    private final SettingService settingService;
    private final PrintJobRepository printJobRepository;
    private final PrintDispatchService printDispatchService;
    private final ReceiptRenderer receiptRenderer;

    @EventListener
    public void onKitchenItemsConfirmed(KitchenItemsConfirmed event) {
        SettingsPayload settings = settingService.getSettings(event.tenantId()).getPayload();
        if (!settings.getHardware().isAutoPrintTickets()) {
            return;
        }
        createAndDispatch(PrinterRole.KITCHEN, PrintJobSourceType.KITCHEN_TICKET,
                event.sessionId(), renderKitchenPayload(event));
    }

    @EventListener
    public void onPaymentCompleted(PaymentCompleted event) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        SettingsPayload settings = settingService.getSettings(tenantId).getPayload();
        if (!settings.getHardware().isAutoPrintTickets() || !settings.getHardware().isPrintCustomerReceipt()) {
            return;
        }
        createAndDispatch(PrinterRole.RECEIPT, PrintJobSourceType.BILL_RECEIPT,
                String.valueOf(event.billId()), receiptRenderer.render(event.billId(), settings));
    }

    private void createAndDispatch(
            PrinterRole role, PrintJobSourceType sourceType, String sourceId, String payload) {
        PrintJob job = PrintJob.builder()
                .id(UUID.randomUUID())
                .role(role)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .payload(payload)
                .status(PrintJobStatus.PENDING)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        // saveAndFlush, not save: PrintJob.tenantId is a Hibernate @TenantId field, generated
        // in-memory only when the INSERT actually executes (flush time), not at persist() —
        // dispatch() below reads job.getTenantId() immediately, and a plain save() (which defers
        // flushing to transaction commit) would hand it a still-null tenantId, making its printer
        // lookup always come up empty on this synchronous first attempt (found+fixed during
        // PRINT-07's manual verification, 2026-08-26 — every kitchen ticket landed PENDING with
        // attempts=0 until the next unrelated agent reconnect happened to flush it).
        printJobRepository.saveAndFlush(job);
        printDispatchService.dispatch(job);
    }

    private String renderKitchenPayload(KitchenItemsConfirmed event) {
        List<OrderItem> items = event.confirmedItems();
        StringBuilder sb = new StringBuilder();
        sb.append("Mesa ").append(event.tableNumber()).append('\n');
        for (OrderItem item : items) {
            sb.append("- ").append(item.getName()).append('\n');
            for (var modifier : item.getModifiers()) {
                sb.append("  · ").append(modifier.getOptionName()).append('\n');
            }
        }
        return sb.toString();
    }
}
