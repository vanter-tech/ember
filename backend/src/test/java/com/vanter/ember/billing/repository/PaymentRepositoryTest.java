package com.vanter.ember.billing.repository;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.SplitMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired BillRepository billRepository;
    @Autowired PaymentRepository paymentRepository;

    private Bill savedBill() {
        return billRepository.save(Bill.builder()
                .sessionId("sess-1").total(new BigDecimal("40.00"))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now()).build());
    }

    @Test
    void save_persistsPayment() {
        Bill bill = savedBill();
        Payment payment = Payment.builder()
                .bill(bill).participantName("Alice").amount(new BigDecimal("25.00"))
                .method(PaymentMethod.DIGITAL).gatewayRef("gw-ref-001")
                .status(PaymentStatus.PENDING).createdAt(LocalDateTime.now()).build();

        Payment saved = paymentRepository.save(payment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBill().getId()).isEqualTo(bill.getId());
        assertThat(saved.getParticipantName()).isEqualTo("Alice");
        assertThat(saved.getAmount()).isEqualByComparingTo("25.00");
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.DIGITAL);
        assertThat(saved.getGatewayRef()).isEqualTo("gw-ref-001");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void findByBillId_returnsAllPaymentsForBill() {
        Bill bill = savedBill();
        paymentRepository.save(Payment.builder()
                .bill(bill).participantName("Alice").amount(new BigDecimal("25.00"))
                .method(PaymentMethod.DIGITAL).status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now()).build());
        paymentRepository.save(Payment.builder()
                .bill(bill).participantName("Bob").amount(new BigDecimal("15.00"))
                .method(PaymentMethod.PHYSICAL).status(PaymentStatus.CONFIRMED)
                .createdAt(LocalDateTime.now()).build());

        List<Payment> payments = paymentRepository.findByBillId(bill.getId());

        assertThat(payments).hasSize(2);
    }

    @Test
    void findByStatus_returnsPendingPayments() {
        Bill bill = savedBill();
        paymentRepository.save(Payment.builder()
                .bill(bill).participantName("Alice").amount(new BigDecimal("25.00"))
                .method(PaymentMethod.DIGITAL).status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now()).build());
        paymentRepository.save(Payment.builder()
                .bill(bill).participantName("Bob").amount(new BigDecimal("15.00"))
                .method(PaymentMethod.PHYSICAL).status(PaymentStatus.CONFIRMED)
                .createdAt(LocalDateTime.now()).build());

        List<Payment> pending = paymentRepository.findByStatus(PaymentStatus.PENDING);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getAmount()).isEqualByComparingTo("25.00");
    }
}
