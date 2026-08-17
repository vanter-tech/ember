package com.vanter.ember.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplitStatus;
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

class RefundRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired BillRepository billRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired RefundRepository refundRepository;

    @Override
    protected void deleteAll() {
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        billRepository.deleteAll();
    }

    private Payment paymentFor(UUID tenantId, String amount) {
        Bill bill = readAs(tenantId, () -> billRepository.save(Bill.builder()
                .sessionId("sess-" + tenantId).total(new BigDecimal(amount))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.PAID)
                .createdAt(LocalDateTime.now()).build()));
        return readAs(tenantId, () -> paymentRepository.save(Payment.builder()
                .bill(bill).participantName("Alice").amount(new BigDecimal(amount))
                .method(PaymentMethod.PHYSICAL).status(PaymentStatus.CONFIRMED)
                .processedBy("user-1").createdAt(LocalDateTime.now()).build()));
    }

    private Refund refundOf(UUID tenantId, Payment payment, String amount) {
        return readAs(tenantId, () -> refundRepository.save(Refund.builder()
                .payment(payment).amount(new BigDecimal(amount)).reason("test refund")
                .refundedBy("user-2").createdAt(LocalDateTime.now()).build()));
    }

    @Test
    void save_stampsTheBoundTenant() {
        Payment payment = paymentFor(TENANT_A, "20.00");
        Refund saved = refundOf(TENANT_A, payment, "20.00");

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void sumByPaymentId_doesNotLeakAnotherTenantsRefund() {
        Payment paymentA = paymentFor(TENANT_A, "20.00");
        refundOf(TENANT_A, paymentA, "5.00");
        refundOf(TENANT_A, paymentA, "3.00");

        assertThat(readAs(TENANT_A, () -> refundRepository.sumByPaymentId(paymentA.getId())))
                .isEqualByComparingTo("8.00");
        assertThat(readAs(TENANT_B, () -> refundRepository.sumByPaymentId(paymentA.getId())))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void sumByPaymentId_isZeroWhenNoRefundsExist() {
        Payment payment = paymentFor(TENANT_A, "20.00");

        assertThat(readAs(TENANT_A, () -> refundRepository.sumByPaymentId(payment.getId())))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void findByPaymentId_doesNotReachAnotherTenantsRefund() {
        Payment paymentA = paymentFor(TENANT_A, "20.00");
        refundOf(TENANT_A, paymentA, "5.00");

        assertThat(readAs(TENANT_B, () -> refundRepository.findByPaymentId(paymentA.getId()))).isEmpty();
        assertThat(readAs(TENANT_A, () -> refundRepository.findByPaymentId(paymentA.getId()))).hasSize(1);
    }

    @Test
    void sumRefundsInWindow_onlyCountsTheBoundTenantsRefundsInRange() {
        Payment paymentA = paymentFor(TENANT_A, "20.00");
        LocalDateTime now = LocalDateTime.now();
        readAs(TENANT_A, () -> refundRepository.save(Refund.builder()
                .payment(paymentA).amount(new BigDecimal("5.00")).reason("in window")
                .refundedBy("user-2").createdAt(now).build()));
        readAs(TENANT_A, () -> refundRepository.save(Refund.builder()
                .payment(paymentA).amount(new BigDecimal("9.00")).reason("out of window")
                .refundedBy("user-2").createdAt(now.minusDays(10)).build()));

        BigDecimal sum = readAs(TENANT_A, () -> refundRepository.sumRefundsInWindow(
                TENANT_A, now.minusHours(1), now.plusHours(1)));

        assertThat(sum).isEqualByComparingTo("5.00");
    }

    @Test
    void findRefundsByDay_groupsByCalendarDay() {
        Payment paymentA = paymentFor(TENANT_A, "20.00");
        LocalDateTime day = LocalDateTime.of(2026, 8, 17, 10, 0);
        readAs(TENANT_A, () -> refundRepository.save(Refund.builder()
                .payment(paymentA).amount(new BigDecimal("5.00")).reason("r1")
                .refundedBy("user-2").createdAt(day).build()));
        readAs(TENANT_A, () -> refundRepository.save(Refund.builder()
                .payment(paymentA).amount(new BigDecimal("3.00")).reason("r2")
                .refundedBy("user-2").createdAt(day.plusHours(2)).build()));

        List<RefundDailyAmount> rows = readAs(TENANT_A, () -> refundRepository.findRefundsByDay(
                TENANT_A, day.minusDays(1), day.plusDays(1)));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.date()).isEqualTo(java.time.LocalDate.of(2026, 8, 17));
            assertThat(row.amount()).isEqualByComparingTo("8.00");
        });
    }
}
