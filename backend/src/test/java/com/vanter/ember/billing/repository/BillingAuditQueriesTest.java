package com.vanter.ember.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.Refund;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BillingAuditQueriesTest extends AbstractTenantIsolationTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 2, 12, 0);

    @Autowired BillRepository billRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired RefundRepository refundRepository;

    @Override
    protected void deleteAll() {
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        billRepository.deleteAll();
    }

    private Bill bill(UUID tenantId, BillStatus status, LocalDateTime voidedAt) {
        return readAs(tenantId, () -> billRepository.save(Bill.builder()
                .sessionId("sess-" + UUID.randomUUID()).total(new BigDecimal("10.00"))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(status).createdAt(NOON)
                .voidedAt(voidedAt).voidedBy(voidedAt == null ? null : "user-1")
                .voidReason(voidedAt == null ? null : "mistake").build()));
    }

    private Payment payment(UUID tenantId, Bill bill) {
        return readAs(tenantId, () -> paymentRepository.save(Payment.builder()
                .bill(bill).participantName("Alice").amount(new BigDecimal("10.00"))
                .method(PaymentMethod.PHYSICAL).status(PaymentStatus.CONFIRMED)
                .processedBy("user-1").createdAt(NOON).build()));
    }

    private Refund refund(UUID tenantId, Payment payment, LocalDateTime at) {
        return readAs(tenantId, () -> refundRepository.save(Refund.builder()
                .payment(payment).amount(new BigDecimal("2.00")).reason("cold food")
                .refundedBy("user-2").createdAt(at).build()));
    }

    @Test
    void findVoidedBetween_returnsVoidedBillsInTheWindowOldestFirst() {
        Bill later = bill(TENANT_A, BillStatus.VOIDED, NOON.plusHours(2));
        Bill earlier = bill(TENANT_A, BillStatus.VOIDED, NOON.plusHours(1));
        bill(TENANT_A, BillStatus.VOIDED, NOON.plusDays(3));
        bill(TENANT_A, BillStatus.OPEN, null);

        List<Bill> found = readAs(TENANT_A,
                () -> billRepository.findVoidedBetween(TENANT_A, NOON, NOON.plusHours(5)));

        assertThat(found).extracting(Bill::getId).containsExactly(earlier.getId(), later.getId());
    }

    @Test
    void findVoidedBetween_includesTheWindowEdges() {
        Bill atStart = bill(TENANT_A, BillStatus.VOIDED, NOON);
        Bill atEnd = bill(TENANT_A, BillStatus.VOIDED, NOON.plusHours(1));

        List<Bill> found = readAs(TENANT_A,
                () -> billRepository.findVoidedBetween(TENANT_A, NOON, NOON.plusHours(1)));

        assertThat(found).extracting(Bill::getId).containsExactly(atStart.getId(), atEnd.getId());
    }

    @Test
    void findVoidedBetween_doesNotLeakAnotherTenantsVoids() {
        bill(TENANT_A, BillStatus.VOIDED, NOON.plusHours(1));

        assertThat(readAs(TENANT_B, () -> billRepository.findVoidedBetween(TENANT_B, NOON, NOON.plusHours(5))))
                .isEmpty();
    }

    @Test
    void findByPaymentIdIn_returnsTheRefundsOfThosePaymentsOldestFirst() {
        Payment p1 = payment(TENANT_A, bill(TENANT_A, BillStatus.PAID, null));
        Payment p2 = payment(TENANT_A, bill(TENANT_A, BillStatus.PAID, null));
        Payment other = payment(TENANT_A, bill(TENANT_A, BillStatus.PAID, null));
        Refund second = refund(TENANT_A, p1, NOON.plusHours(2));
        Refund first = refund(TENANT_A, p2, NOON.plusHours(1));
        refund(TENANT_A, other, NOON);

        List<Refund> found = readAs(TENANT_A,
                () -> refundRepository.findByPaymentIdIn(List.of(p1.getId(), p2.getId())));

        assertThat(found).extracting(Refund::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void findByPaymentIdIn_doesNotReachAnotherTenantsRefund() {
        Payment paymentA = payment(TENANT_A, bill(TENANT_A, BillStatus.PAID, null));
        refund(TENANT_A, paymentA, NOON);

        assertThat(readAs(TENANT_B, () -> refundRepository.findByPaymentIdIn(List.of(paymentA.getId()))))
                .isEmpty();
    }
}
