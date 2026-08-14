package com.vanter.ember.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired BillRepository billRepository;
    @Autowired PaymentRepository paymentRepository;

    @Override
    protected void deleteAll() {
        paymentRepository.deleteAll();
        billRepository.deleteAll();
    }

    private Bill billOf(UUID tenantId) {
        return readAs(
                tenantId,
                () ->
                        billRepository.save(
                                Bill.builder()
                                        .sessionId("sess-" + tenantId)
                                        .total(new BigDecimal("40.00"))
                                        .splitMethod(SplitMethod.BY_CONSUMPTION)
                                        .status(BillStatus.OPEN)
                                        .createdAt(LocalDateTime.now())
                                        .build()));
    }

    private Payment paymentSavedFor(UUID tenantId, String participantName, PaymentStatus status) {
        Bill bill = billOf(tenantId);
        return readAs(
                tenantId,
                () ->
                        paymentRepository.save(
                                Payment.builder()
                                        .bill(bill)
                                        .participantName(participantName)
                                        .amount(new BigDecimal("25.00"))
                                        .method(PaymentMethod.DIGITAL)
                                        .status(status)
                                        .createdAt(LocalDateTime.now())
                                        .build()));
    }

    @Test
    void save_stampsTheBoundTenant() {
        Payment saved = paymentSavedFor(TENANT_A, "Alice", PaymentStatus.PENDING);

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findByBillId_doesNotReachAnotherTenantsPayments() {
        Payment tenantAPayment = paymentSavedFor(TENANT_A, "Alice", PaymentStatus.PENDING);
        Long billId = tenantAPayment.getBill().getId();

        assertThat(readAs(TENANT_B, () -> paymentRepository.findByBillId(billId))).isEmpty();
        assertThat(readAs(TENANT_A, () -> paymentRepository.findByBillId(billId))).hasSize(1);
    }

    @Test
    void findByStatus_onlyReturnsTheBoundTenantsPayments() {
        paymentSavedFor(TENANT_A, "Alice", PaymentStatus.PENDING);
        paymentSavedFor(TENANT_B, "Bob", PaymentStatus.PENDING);

        assertThat(readAs(TENANT_B, () -> paymentRepository.findByStatus(PaymentStatus.PENDING)))
                .singleElement()
                .satisfies(payment -> assertThat(payment.getParticipantName()).isEqualTo("Bob"));
    }

    /** A tenant may only hold one bill per session, so several payments have to share one bill. */
    private Payment paymentOn(Bill bill, UUID tenantId, String participantName, PaymentStatus status) {
        return readAs(
                tenantId,
                () ->
                        paymentRepository.save(
                                Payment.builder()
                                        .bill(bill)
                                        .participantName(participantName)
                                        .amount(new BigDecimal("25.00"))
                                        .method(PaymentMethod.DIGITAL)
                                        .status(status)
                                        .createdAt(LocalDateTime.now())
                                        .build()));
    }

    @Test
    void sumConfirmedRevenue_onlyAggregatesTheBoundTenantsConfirmedPayments() {
        paymentSavedFor(TENANT_A, "Alice", PaymentStatus.CONFIRMED);
        Bill billB = billOf(TENANT_B);
        paymentOn(billB, TENANT_B, "Bob", PaymentStatus.CONFIRMED);
        paymentOn(billB, TENANT_B, "Carla", PaymentStatus.CONFIRMED);
        paymentOn(billB, TENANT_B, "Dan", PaymentStatus.PENDING);

        BigDecimal revenueForB = readAs(
                TENANT_B,
                () -> paymentRepository.sumConfirmedRevenue(
                        TENANT_B, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)));

        assertThat(revenueForB).isEqualByComparingTo("50.00");
    }

    @Test
    void sumConfirmedRevenue_isNullOutsideTheWindow() {
        paymentSavedFor(TENANT_A, "Alice", PaymentStatus.CONFIRMED);

        BigDecimal revenue = readAs(
                TENANT_A,
                () -> paymentRepository.sumConfirmedRevenue(
                        TENANT_A, LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(2)));

        assertThat(revenue).isNull();
    }

    private void paymentOn(
            Bill bill, UUID tenantId, String participantName, String amount, LocalDateTime createdAt) {
        readAs(
                tenantId,
                () ->
                        paymentRepository.save(
                                Payment.builder()
                                        .bill(bill)
                                        .participantName(participantName)
                                        .amount(new BigDecimal(amount))
                                        .method(PaymentMethod.DIGITAL)
                                        .status(PaymentStatus.CONFIRMED)
                                        .createdAt(createdAt)
                                        .build()));
    }

    @Test
    void findConfirmedRevenueByDay_sumsTheBoundTenantsRevenuePerCalendarDay() {
        LocalDateTime firstDay = LocalDateTime.of(2026, 8, 10, 20, 0);
        Bill billA = billOf(TENANT_A);
        paymentOn(billA, TENANT_A, "Noise", "999.00", firstDay);
        Bill billB = billOf(TENANT_B);
        paymentOn(billB, TENANT_B, "Bob", "30.00", firstDay);
        paymentOn(billB, TENANT_B, "Carla", "20.00", firstDay.plusHours(2));
        paymentOn(billB, TENANT_B, "Dan", "10.00", firstDay.plusDays(2));
        paymentOn(billB, TENANT_B, "Eve", "5.00", firstDay.plusDays(5));

        List<PaymentDailyRevenue> daysForB = readAs(
                TENANT_B,
                () -> paymentRepository.findConfirmedRevenueByDay(
                        TENANT_B, firstDay.minusDays(1), firstDay.plusDays(3)));

        assertThat(daysForB).hasSize(2);
        assertThat(daysForB.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(daysForB.get(0).revenue()).isEqualByComparingTo("50.00");
        assertThat(daysForB.get(1).date()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(daysForB.get(1).revenue()).isEqualByComparingTo("10.00");
    }

    @Test
    void findConfirmedRevenueByDay_ignoresPendingPayments() {
        LocalDateTime when = LocalDateTime.of(2026, 8, 10, 20, 0);
        Bill billA = billOf(TENANT_A);
        paymentOn(billA, TENANT_A, "Alice", "40.00", when);
        readAs(
                TENANT_A,
                () ->
                        paymentRepository.save(
                                Payment.builder()
                                        .bill(billA)
                                        .participantName("Pending")
                                        .amount(new BigDecimal("999.00"))
                                        .method(PaymentMethod.DIGITAL)
                                        .status(PaymentStatus.PENDING)
                                        .createdAt(when)
                                        .build()));

        List<PaymentDailyRevenue> days = readAs(
                TENANT_A,
                () -> paymentRepository.findConfirmedRevenueByDay(
                        TENANT_A, when.minusDays(1), when.plusDays(1)));

        assertThat(days).singleElement().satisfies(day -> assertThat(day.revenue()).isEqualByComparingTo("40.00"));
    }

    @Test
    void findById_doesNotLeakAnotherTenantsPayment() {
        Long id = paymentSavedFor(TENANT_A, "Alice", PaymentStatus.CONFIRMED).getId();

        assertThat(readAs(TENANT_B, () -> paymentRepository.findById(id))).isEmpty();
        assertThat(readAs(TENANT_A, () -> paymentRepository.findById(id))).isPresent();
    }
}
