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
import java.time.LocalDateTime;
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

    @Test
    void findById_doesNotLeakAnotherTenantsPayment() {
        Long id = paymentSavedFor(TENANT_A, "Alice", PaymentStatus.CONFIRMED).getId();

        assertThat(readAs(TENANT_B, () -> paymentRepository.findById(id))).isEmpty();
        assertThat(readAs(TENANT_A, () -> paymentRepository.findById(id))).isPresent();
    }
}
