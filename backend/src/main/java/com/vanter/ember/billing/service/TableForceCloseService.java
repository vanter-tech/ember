package com.vanter.ember.billing.service;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets an ADMIN close a table that got stuck (typically left open for days with consumption nobody
 * can charge or close: the only other exits are paying it, or the assigned waiter cancelling it
 * while it is still empty). Lives in {@code billing} because it needs the session, the bill and the
 * payments, and {@code billing} already depends on {@code session}, never the other way round.
 *
 * <p>A table with a confirmed payment is refused: that money is real and must be finished or
 * refunded through the normal flow, not silently written off. Sales are unaffected either way —
 * revenue is computed from confirmed payments only.
 */
@Service
@RequiredArgsConstructor
public class TableForceCloseService {

    private final SessionService sessionService;
    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final BillingService billingService;

    @Transactional
    public void forceClose(String sessionId, String adminEmail, String reason) {
        // Tenant-scoped: another restaurant's table is indistinguishable from a missing one (404).
        Session session = sessionService.findById(sessionId);
        if (session.getStatus() == SessionStatus.CLOSED) {
            throw new IllegalStateException("The table is already closed: " + sessionId);
        }

        Bill bill = billRepository.findBySessionIdAndStatusNot(sessionId, BillStatus.VOIDED).orElse(null);
        if (bill != null) {
            if (paymentRepository.existsByBillIdAndStatus(bill.getId(), PaymentStatus.CONFIRMED)) {
                throw new IllegalStateException(
                        "The table already has confirmed payments; finish or refund them instead of closing it");
            }
            if (bill.getStatus() == BillStatus.OPEN) {
                billingService.voidBill(bill.getId(), "Cerrada por administrador: " + reason, adminEmail);
            }
        }

        sessionService.closeByAdmin(sessionId, adminEmail, reason);
    }
}
