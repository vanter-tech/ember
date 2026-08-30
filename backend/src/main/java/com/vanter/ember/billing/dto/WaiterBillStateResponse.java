package com.vanter.ember.billing.dto;

import com.vanter.ember.billing.model.BillSplit;
import java.math.BigDecimal;
import java.util.List;

/**
 * The current, non-voided bill for a session as both the waiter table view and a (re)joining
 * diner need it: total, every split, and the digital payments still awaiting a waiter's
 * confirmation. Returned by {@code GET /billing/sessions/{sessionId}/bill} so a page that missed
 * (or dropped) the {@code BILL_READY} WebSocket frame can rehydrate itself.
 */
public record WaiterBillStateResponse(
        Long id,
        BigDecimal total,
        List<BillSplit> splits,
        List<PendingDigitalPayment> pendingDigitalPayments) {

    public record PendingDigitalPayment(Long id, String participantName, BigDecimal amount) {}
}
