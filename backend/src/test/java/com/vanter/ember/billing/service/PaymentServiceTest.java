package com.vanter.ember.billing.service;

import com.vanter.ember.billing.event.PaymentCompleted;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.repository.CashShiftRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.service.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock BillRepository billRepository;
    @Mock BillSplitRepository billSplitRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock SessionService sessionService;
    @Mock CashShiftRepository cashShiftRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks PaymentService paymentService;

    private static final UUID TABLE_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void bindTenant() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private Bill sampleBill() {
        return Bill.builder()
                .id(1L).sessionId("sess-1").total(new BigDecimal("22.50"))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now()).build();
    }

    private CashShift openShift() {
        return CashShift.builder().id(9L).status(CashShiftStatus.OPEN)
                .openingFloat(BigDecimal.TEN).openedBy("user-1").build();
    }

    private User waiterUser() {
        return User.builder().id("user-1").email("alice@ember.local").role(Role.WAITER).build();
    }

    private BillSplit unpaidSplit(Bill bill, String participant, String amount) {
        return BillSplit.builder()
                .id(10L).bill(bill).participantName(participant)
                .amount(new BigDecimal(amount)).paid(false).build();
    }

    private Session sampleSession() {
        return Session.builder().id("sess-1").tableId(TABLE_ID).build();
    }

    @Test
    void registerPhysicalPayment_createsConfirmedPhysicalPayment() {
        Bill bill = sampleBill();
        BillSplit split = unpaidSplit(bill, "Alice", "12.50");
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));
        when(billSplitRepository.findByBillId(1L))
                .thenReturn(List.of(unpaidSplit(bill, "Bob", "10.00")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.registerPhysicalPayment(
                1L, "Alice", new BigDecimal("12.50"), "alice@ember.local");

        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.PHYSICAL);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(payment.getAmount()).isEqualByComparingTo("12.50");
        assertThat(payment.getBill().getId()).isEqualTo(1L);
        assertThat(payment.getCashShiftId()).isEqualTo(9L);
        assertThat(payment.getProcessedBy()).isEqualTo("user-1");
        assertThat(payment.getCreatedAt()).isNotNull();
    }

    @Test
    void registerPhysicalPayment_throwsWhenNoOpenShift() {
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.registerPhysicalPayment(
                1L, "Alice", new BigDecimal("12.50"), "alice@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registerPhysicalPayment_marksSplitAsPaid() {
        Bill bill = sampleBill();
        BillSplit split = unpaidSplit(bill, "Alice", "12.50");
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));
        when(billSplitRepository.findByBillId(1L))
                .thenReturn(List.of(unpaidSplit(bill, "Bob", "10.00")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.registerPhysicalPayment(1L, "Alice", new BigDecimal("12.50"), "alice@ember.local");

        ArgumentCaptor<BillSplit> captor = ArgumentCaptor.forClass(BillSplit.class);
        verify(billSplitRepository).save(captor.capture());
        assertThat(captor.getValue().isPaid()).isTrue();
    }

    @Test
    void registerPhysicalPayment_broadcastsSplitPaidToSessionTopic() {
        Bill bill = sampleBill();
        BillSplit split = unpaidSplit(bill, "Alice", "12.50");
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));
        when(billSplitRepository.findByBillId(1L))
                .thenReturn(List.of(unpaidSplit(bill, "Bob", "10.00")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.registerPhysicalPayment(1L, "Alice", new BigDecimal("12.50"), "alice@ember.local");

        ArgumentCaptor<com.vanter.ember.billing.dto.SplitPaidMessage> captor =
                ArgumentCaptor.forClass(com.vanter.ember.billing.dto.SplitPaidMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/session/sess-1"), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("SPLIT_PAID");
        assertThat(captor.getValue().billId()).isEqualTo(1L);
        assertThat(captor.getValue().participantName()).isEqualTo("Alice");
        assertThat(captor.getValue().paid()).isTrue();
    }

    @Test
    void registerPhysicalPayment_publishesPaymentCompletedWhenAllSplitsPaid() {
        Bill bill = sampleBill();
        BillSplit aliceSplit = unpaidSplit(bill, "Alice", "12.50");
        BillSplit bobSplitPaid = BillSplit.builder()
                .id(11L).bill(bill).participantName("Bob")
                .amount(new BigDecimal("10.00")).paid(true).build();
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(aliceSplit));
        when(billSplitRepository.findByBillId(1L)).thenReturn(List.of(aliceSplit, bobSplitPaid));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionService.findById("sess-1")).thenReturn(sampleSession());

        paymentService.registerPhysicalPayment(1L, "Alice", new BigDecimal("12.50"), "alice@ember.local");

        ArgumentCaptor<PaymentCompleted> captor = ArgumentCaptor.forClass(PaymentCompleted.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo("sess-1");
        assertThat(captor.getValue().tableId()).isEqualTo(TABLE_ID);
        assertThat(captor.getValue().billId()).isEqualTo(1L);
    }

    @Test
    void registerPhysicalPayment_doesNotPublishEventWhenSplitsStillUnpaid() {
        Bill bill = sampleBill();
        BillSplit aliceSplit = unpaidSplit(bill, "Alice", "12.50");
        BillSplit bobSplit = unpaidSplit(bill, "Bob", "10.00");
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(aliceSplit));
        when(billSplitRepository.findByBillId(1L)).thenReturn(List.of(aliceSplit, bobSplit));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.registerPhysicalPayment(1L, "Alice", new BigDecimal("12.50"), "alice@ember.local");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void registerPhysicalPayment_throwsWhenBillNotFound() {
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(billRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.registerPhysicalPayment(99L, "Alice", new BigDecimal("12.50"), "alice@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void registerPhysicalPayment_throwsWhenSplitNotFound() {
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sampleBill()));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.registerPhysicalPayment(1L, "Unknown", new BigDecimal("12.50"), "alice@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void registerPhysicalPayment_throwsWhenAmountDoesNotMatchSplit() {
        Bill bill = sampleBill();
        BillSplit split = unpaidSplit(bill, "Alice", "12.50");
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));

        assertThatThrownBy(() ->
                paymentService.registerPhysicalPayment(1L, "Alice", new BigDecimal("0.01"), "alice@ember.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    // --- initiateDigitalPayment tests ---

    @Test
    void initiateDigitalPayment_createsPendingDigitalPayment() {
        Bill bill = sampleBill();
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(unpaidSplit(bill, "Alice", "12.50")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.initiateDigitalPayment(
                1L, "Alice", new BigDecimal("12.50"), "alice@ember.local");

        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.DIGITAL);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("12.50");
        assertThat(payment.getParticipantName()).isEqualTo("Alice");
        assertThat(payment.getBill().getId()).isEqualTo(1L);
        assertThat(payment.getProcessedBy()).isEqualTo("user-1");
    }

    @Test
    void initiateDigitalPayment_setsNonNullGatewayRef() {
        Bill bill = sampleBill();
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(unpaidSplit(bill, "Alice", "12.50")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.initiateDigitalPayment(
                1L, "Alice", new BigDecimal("12.50"), "alice@ember.local");

        assertThat(payment.getGatewayRef()).isNotBlank();
    }

    @Test
    void initiateDigitalPayment_broadcastsPaymentInitiatedToSessionTopic() {
        Bill bill = sampleBill();
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(unpaidSplit(bill, "Alice", "12.50")));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(21L);
            return p;
        });

        paymentService.initiateDigitalPayment(1L, "Alice", new BigDecimal("12.50"), "alice@ember.local");

        ArgumentCaptor<com.vanter.ember.billing.dto.DigitalPaymentInitiatedMessage> captor =
                ArgumentCaptor.forClass(com.vanter.ember.billing.dto.DigitalPaymentInitiatedMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/session/sess-1"), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("DIGITAL_PAYMENT_INITIATED");
        assertThat(captor.getValue().paymentId()).isEqualTo(21L);
        assertThat(captor.getValue().billId()).isEqualTo(1L);
        assertThat(captor.getValue().participantName()).isEqualTo("Alice");
        assertThat(captor.getValue().amount()).isEqualByComparingTo("12.50");
    }

    @Test
    void initiateDigitalPayment_throwsWhenBillNotFound() {
        when(billRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.initiateDigitalPayment(99L, "Alice", new BigDecimal("12.50"), "alice@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void initiateDigitalPayment_throwsWhenSplitNotFound() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(sampleBill()));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.initiateDigitalPayment(1L, "Alice", new BigDecimal("12.50"), "alice@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void initiateDigitalPayment_throwsWhenAmountDoesNotMatchSplit() {
        Bill bill = sampleBill();
        BillSplit split = unpaidSplit(bill, "Alice", "12.50");
        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));

        assertThatThrownBy(() ->
                paymentService.initiateDigitalPayment(1L, "Alice", new BigDecimal("5.00"), "alice@ember.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    // --- confirmDigitalPayment tests ---

    private Payment pendingDigitalPayment(Bill bill, String participant) {
        return Payment.builder()
                .id(20L).bill(bill).participantName(participant)
                .amount(new BigDecimal("12.50")).method(PaymentMethod.DIGITAL)
                .status(PaymentStatus.PENDING).gatewayRef("STUB-abc123")
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void confirmDigitalPayment_setsStatusToConfirmed() {
        Bill bill = sampleBill();
        Payment payment = pendingDigitalPayment(bill, "Alice");
        when(paymentRepository.findById(20L)).thenReturn(Optional.of(payment));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(unpaidSplit(bill, "Alice", "12.50")));
        when(billSplitRepository.findByBillId(1L))
                .thenReturn(List.of(unpaidSplit(bill, "Bob", "10.00")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment confirmed = paymentService.confirmDigitalPayment(20L);

        assertThat(confirmed.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void confirmDigitalPayment_marksSplitAsPaid() {
        Bill bill = sampleBill();
        Payment payment = pendingDigitalPayment(bill, "Alice");
        BillSplit split = unpaidSplit(bill, "Alice", "12.50");
        when(paymentRepository.findById(20L)).thenReturn(Optional.of(payment));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));
        when(billSplitRepository.findByBillId(1L))
                .thenReturn(List.of(unpaidSplit(bill, "Bob", "10.00")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.confirmDigitalPayment(20L);

        ArgumentCaptor<BillSplit> captor = ArgumentCaptor.forClass(BillSplit.class);
        verify(billSplitRepository).save(captor.capture());
        assertThat(captor.getValue().isPaid()).isTrue();
    }

    @Test
    void confirmDigitalPayment_broadcastsSplitPaidToSessionTopic() {
        Bill bill = sampleBill();
        Payment payment = pendingDigitalPayment(bill, "Alice");
        BillSplit split = unpaidSplit(bill, "Alice", "12.50");
        when(paymentRepository.findById(20L)).thenReturn(Optional.of(payment));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));
        when(billSplitRepository.findByBillId(1L))
                .thenReturn(List.of(unpaidSplit(bill, "Bob", "10.00")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.confirmDigitalPayment(20L);

        ArgumentCaptor<com.vanter.ember.billing.dto.SplitPaidMessage> captor =
                ArgumentCaptor.forClass(com.vanter.ember.billing.dto.SplitPaidMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/session/sess-1"), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("SPLIT_PAID");
        assertThat(captor.getValue().participantName()).isEqualTo("Alice");
        assertThat(captor.getValue().paid()).isTrue();
    }

    @Test
    void confirmDigitalPayment_publishesPaymentCompletedWhenAllSplitsPaid() {
        Bill bill = sampleBill();
        Payment payment = pendingDigitalPayment(bill, "Alice");
        BillSplit aliceSplit = unpaidSplit(bill, "Alice", "12.50");
        BillSplit bobPaid = BillSplit.builder().id(11L).bill(bill)
                .participantName("Bob").amount(new BigDecimal("10.00")).paid(true).build();
        when(paymentRepository.findById(20L)).thenReturn(Optional.of(payment));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(aliceSplit));
        when(billSplitRepository.findByBillId(1L)).thenReturn(List.of(aliceSplit, bobPaid));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionService.findById("sess-1")).thenReturn(sampleSession());

        paymentService.confirmDigitalPayment(20L);

        ArgumentCaptor<PaymentCompleted> captor = ArgumentCaptor.forClass(PaymentCompleted.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().billId()).isEqualTo(1L);
        assertThat(captor.getValue().sessionId()).isEqualTo("sess-1");
    }

    @Test
    void confirmDigitalPayment_throwsWhenPaymentNotFound() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmDigitalPayment(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
