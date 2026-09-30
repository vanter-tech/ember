package com.vanter.ember.cashregister.listener;

import com.vanter.ember.billing.event.PhysicalPaymentRegistered;
import com.vanter.ember.cashregister.model.CashDrawerEvent;
import com.vanter.ember.cashregister.model.CashDrawerEventStatus;
import com.vanter.ember.cashregister.model.CashDrawerEventType;
import com.vanter.ember.cashregister.repository.CashDrawerEventRepository;
import com.vanter.ember.session.service.SessionService;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records a pending cash receipt for the accountant whenever a waiter confirms a physical payment.
 * Runs synchronously in the payment's transaction. Only the table-number lookup is best-effort: a
 * failure there must not block the payment, and the receipt is still worth recording.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CashDrawerEventListener {

    private final CashDrawerEventRepository repository;
    private final SessionService sessionService;
    private final DiningTableRepository diningTableRepository;

    @EventListener
    public void onPhysicalPayment(PhysicalPaymentRegistered event) {
        repository.save(CashDrawerEvent.builder()
                .id(UUID.randomUUID())
                .type(CashDrawerEventType.CASH_SALE)
                .status(CashDrawerEventStatus.PENDING)
                .paymentId(event.paymentId())
                .cashShiftId(event.cashShiftId())
                .tableNumber(resolveTableNumber(event.tenantId(), event.sessionId()))
                .amount(event.amount())
                .createdBy(event.processedBy())
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Integer resolveTableNumber(UUID tenantId, String sessionId) {
        try {
            UUID tableId = sessionService.findById(sessionId).getTableId();
            return diningTableRepository.findByRestaurantIdAndIdIn(tenantId, Set.of(tableId)).stream()
                    .findFirst().map(DiningTables::getTableNumber).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Could not resolve the table number for session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }
}
