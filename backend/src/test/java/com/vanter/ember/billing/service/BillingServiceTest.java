package com.vanter.ember.billing.service;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillSplitStatus;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.service.SessionService;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.vanter.ember.config.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock BillRepository billRepository;
    @Mock BillSplitRepository billSplitRepository;
    @Mock SessionService sessionService;
    @Mock PaymentRepository paymentRepository;
    @Mock UserRepository userRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock SettingService settingService;
    @InjectMocks BillingService billingService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private static RestaurantSettings settingsWithTaxRate(Double taxRatePercent) {
        RestaurantSettings settings = new RestaurantSettings();
        SettingsPayload payload = new SettingsPayload();
        payload.getBilling().setTaxRate(taxRatePercent);
        settings.setPayload(payload);
        return settings;
    }

    @BeforeEach
    void bindTenant() {
        TenantContextHolder.setTenantId(TENANT_ID);
        // Most tests don't care about tax — default to 0% here (via `lenient` so voidBill/etc.
        // tests that never reach taxMultiplier() don't trip strict-stubbing) and override per-test
        // where the tax calculation itself (E-05) is what's under test.
        lenient().when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWithTaxRate(0.0));
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private Session sessionWithMixedItems() {
        return Session.builder()
                .id("sess-1").tableId(UUID.randomUUID()).status(SessionStatus.OPEN)
                .items(new ArrayList<>(List.of(
                        OrderItem.builder().id("i-1").name("Tacos")
                                .price(new BigDecimal("12.50")).participantName("Alice")
                                .status(OrderItemStatus.DELIVERED).build(),
                        OrderItem.builder().id("i-2").name("Burger")
                                .price(new BigDecimal("10.00")).participantName("Bob")
                                .status(OrderItemStatus.READY).build(),
                        OrderItem.builder().id("i-3").name("Salad")
                                .price(new BigDecimal("8.00")).participantName("Alice")
                                .status(OrderItemStatus.PENDING).build()
                ))).build();
    }

    @Test
    void calculateBill_sumsPricesOfDeliveredAndReadyItems() {
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED)).thenReturn(Optional.empty());
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill bill = billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION);

        assertThat(bill.getTotal()).isEqualByComparingTo("22.50");
    }

    @Test
    void calculateBill_excludesPendingAndPreparingItems() {
        Session session = Session.builder().id("sess-1").tableId(UUID.randomUUID()).status(SessionStatus.OPEN)
                .items(new ArrayList<>(List.of(
                        OrderItem.builder().id("i-1").price(new BigDecimal("20.00"))
                                .status(OrderItemStatus.DELIVERED).build(),
                        OrderItem.builder().id("i-2").price(new BigDecimal("5.00"))
                                .status(OrderItemStatus.PREPARING).build(),
                        OrderItem.builder().id("i-3").price(new BigDecimal("3.00"))
                                .status(OrderItemStatus.PENDING).build()
                ))).build();
        when(sessionService.findById("sess-1")).thenReturn(session);
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED)).thenReturn(Optional.empty());
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill bill = billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION);

        assertThat(bill.getTotal()).isEqualByComparingTo("20.00");
    }

    @Test
    void calculateBill_throwsWhenSessionHasNoBillableItems() {
        Session session = Session.builder().id("sess-1").tableId(UUID.randomUUID()).status(SessionStatus.OPEN)
                .items(new ArrayList<>(List.of(
                        OrderItem.builder().id("i-1").price(new BigDecimal("8.00"))
                                .status(OrderItemStatus.PENDING).build()
                ))).build();
        when(sessionService.findById("sess-1")).thenReturn(session);
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No billable items");
    }

    @Test
    void calculateBill_throwsWhenSessionNotOpen() {
        Session closed = Session.builder().id("sess-1").tableId(UUID.randomUUID()).status(SessionStatus.CLOSED)
                .items(new ArrayList<>()).build();
        when(sessionService.findById("sess-1")).thenReturn(closed);
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not open");
    }

    @Test
    void calculateBill_throwsWhenEmptyItemsList() {
        Session session = Session.builder().id("sess-1").tableId(UUID.randomUUID()).status(SessionStatus.OPEN)
                .items(new ArrayList<>()).build();
        when(sessionService.findById("sess-1")).thenReturn(session);
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No billable items");
    }

    @Test
    void calculateBill_throwsWhenSessionAlreadyBilled() {
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED))
                .thenReturn(Optional.of(Bill.builder().id(1L).build()));

        assertThatThrownBy(() -> billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already billed");
    }

    @Test
    void calculateBill_succeedsAfterThePreviousBillWasVoided() {
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED))
                .thenReturn(Optional.empty());
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill bill = billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION);

        assertThat(bill.getTotal()).isEqualByComparingTo("22.50");
        assertThat(bill.getStatus()).isEqualTo(BillStatus.OPEN);
    }

    @Test
    void calculateBill_persistsBillWithOpenStatusAndCorrectFields() {
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED)).thenReturn(Optional.empty());
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        billingService.calculateBill("sess-1", SplitMethod.EQUAL_PARTS);

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository).save(captor.capture());
        Bill saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(BillStatus.OPEN);
        assertThat(saved.getSessionId()).isEqualTo("sess-1");
        assertThat(saved.getSplitMethod()).isEqualTo(SplitMethod.EQUAL_PARTS);
        assertThat(saved.getTotal()).isEqualByComparingTo("22.50");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void calculateBill_appliesConfiguredTaxRate_QA_E05() {
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWithTaxRate(16.0));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED)).thenReturn(Optional.empty());
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill bill = billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION);

        // subtotal 22.50 * 1.16 = 26.10
        assertThat(bill.getTotal()).isEqualByComparingTo("26.10");
    }

    @Test
    void calculateBill_treatsMissingTaxRateAsZero_QA_E05() {
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWithTaxRate(null));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED)).thenReturn(Optional.empty());
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill bill = billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION);

        assertThat(bill.getTotal()).isEqualByComparingTo("22.50");
    }

    // --- splitByConsumption tests ---

    private Bill savedBill() {
        return Bill.builder()
                .id(1L).sessionId("sess-1").total(new BigDecimal("22.50"))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void splitByConsumption_createsOneSplitPerParticipant() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(savedBill()));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<BillSplit> splits = billingService.splitByConsumption(1L);

        assertThat(splits).hasSize(2);
    }

    @Test
    void splitByConsumption_calculatesCorrectAmountPerParticipant() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(savedBill()));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<BillSplit> splits = billingService.splitByConsumption(1L);

        BillSplit alice = splits.stream()
                .filter(s -> "Alice".equals(s.getParticipantName())).findFirst().orElseThrow();
        BillSplit bob = splits.stream()
                .filter(s -> "Bob".equals(s.getParticipantName())).findFirst().orElseThrow();
        assertThat(alice.getAmount()).isEqualByComparingTo("12.50");
        assertThat(bob.getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void splitByConsumption_appliesConfiguredTaxRateProportionally_QA_E05() {
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWithTaxRate(16.0));
        when(billRepository.findById(1L)).thenReturn(Optional.of(savedBill()));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<BillSplit> splits = billingService.splitByConsumption(1L);

        BillSplit alice = splits.stream()
                .filter(s -> "Alice".equals(s.getParticipantName())).findFirst().orElseThrow();
        BillSplit bob = splits.stream()
                .filter(s -> "Bob".equals(s.getParticipantName())).findFirst().orElseThrow();
        // 12.50 * 1.16 = 14.50, 10.00 * 1.16 = 11.60 — sum matches a 16%-taxed bill total.
        assertThat(alice.getAmount()).isEqualByComparingTo("14.50");
        assertThat(bob.getAmount()).isEqualByComparingTo("11.60");
    }

    @Test
    void splitByConsumption_splitsAreLinkedToBill() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(savedBill()));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<BillSplit> splits = billingService.splitByConsumption(1L);

        assertThat(splits).allMatch(s -> s.getBill().getId().equals(1L));
    }

    @Test
    void splitByConsumption_splitsAreNotPaidByDefault() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(savedBill()));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<BillSplit> splits = billingService.splitByConsumption(1L);

        assertThat(splits).allMatch(s -> s.getStatus() == BillSplitStatus.UNPAID);
    }

    @Test
    void splitByConsumption_persistsSplitsViaRepository() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(savedBill()));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        billingService.splitByConsumption(1L);

        verify(billSplitRepository).saveAll(anyList());
    }

    @Test
    void splitByConsumption_throwsWhenBillNotFound() {
        when(billRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.splitByConsumption(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- splitEqually tests ---

    private Session sessionWithThreeParticipants() {
        return Session.builder()
                .id("sess-1").tableId(UUID.randomUUID())
                .participants(new ArrayList<>(List.of(
                        Participant.builder().userId("u-1").name("Alice").build(),
                        Participant.builder().userId("u-2").name("Bob").build(),
                        Participant.builder().userId("u-3").name("Carol").build()
                )))
                .items(new ArrayList<>()).build();
    }

    private Bill billWithTotal(String total) {
        return Bill.builder()
                .id(1L).sessionId("sess-1")
                .total(new BigDecimal(total))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void splitEqually_createsSplitForEachParticipant() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(billWithTotal("30.00")));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithThreeParticipants());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<BillSplit> splits = billingService.splitEqually(1L, 3);

        assertThat(splits).hasSize(3);
    }

    @Test
    void splitEqually_dividesTotalEvenlyWhenDivisible() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(billWithTotal("30.00")));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithThreeParticipants());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<BillSplit> splits = billingService.splitEqually(1L, 3);

        assertThat(splits).allMatch(s -> s.getAmount().compareTo(new BigDecimal("10.00")) == 0);
    }

    @Test
    void splitEqually_lastParticipantAbsorbsRemainderCents() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(billWithTotal("10.00")));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithThreeParticipants());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<BillSplit> splits = billingService.splitEqually(1L, 3);

        BigDecimal share = new BigDecimal("3.33");
        BigDecimal lastShare = new BigDecimal("3.34");
        long regularCount = splits.stream()
                .filter(s -> s.getAmount().compareTo(share) == 0).count();
        long lastCount = splits.stream()
                .filter(s -> s.getAmount().compareTo(lastShare) == 0).count();
        assertThat(regularCount).isEqualTo(2);
        assertThat(lastCount).isEqualTo(1);
    }

    @Test
    void splitEqually_totalOfAllSplitsEqualsOriginalTotal() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(billWithTotal("10.00")));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithThreeParticipants());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<BillSplit> splits = billingService.splitEqually(1L, 3);

        BigDecimal sumOfSplits = splits.stream()
                .map(BillSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumOfSplits).isEqualByComparingTo("10.00");
    }

    @Test
    void splitEqually_splitsAreNotPaidByDefault() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(billWithTotal("30.00")));
        when(sessionService.findById("sess-1")).thenReturn(sessionWithThreeParticipants());
        when(billSplitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<BillSplit> splits = billingService.splitEqually(1L, 3);

        assertThat(splits).allMatch(s -> s.getStatus() == BillSplitStatus.UNPAID);
    }

    @Test
    void splitEqually_throwsWhenBillNotFound() {
        when(billRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.splitEqually(99L, 3))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void splitEqually_throwsIllegalArgumentForNonPositiveParticipantCount() {
        // Guards against a division by zero (ArithmeticException -> 500) if a bad count ever
        // reaches this method through a path other than the request DTO's validation.
        assertThatThrownBy(() -> billingService.splitEqually(1L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1");
    }

    // --- voidBill tests ---

    private Bill openBill() {
        return Bill.builder()
                .id(1L).sessionId("sess-1").total(new BigDecimal("22.50"))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void voidBill_setsStatusVoidedAndStampsReason() {
        Bill bill = openBill();
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndStatus(1L, PaymentStatus.CONFIRMED)).thenReturn(false);
        when(userRepository.findByEmail("waiter@ember.local"))
                .thenReturn(Optional.of(User.builder().id("user-1").role(Role.WAITER).build()));
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill voided = billingService.voidBill(1L, "wrong split method", "waiter@ember.local");

        assertThat(voided.getStatus()).isEqualTo(BillStatus.VOIDED);
        assertThat(voided.getVoidReason()).isEqualTo("wrong split method");
        assertThat(voided.getVoidedBy()).isEqualTo("user-1");
        assertThat(voided.getVoidedAt()).isNotNull();
    }

    @Test
    void voidBill_broadcastsBillVoidedToSessionTopic() {
        Bill bill = openBill();
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndStatus(1L, PaymentStatus.CONFIRMED)).thenReturn(false);
        when(userRepository.findByEmail("waiter@ember.local"))
                .thenReturn(Optional.of(User.builder().id("user-1").role(Role.WAITER).build()));
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        billingService.voidBill(1L, "wrong split method", "waiter@ember.local");

        ArgumentCaptor<com.vanter.ember.billing.dto.BillVoidedMessage> captor =
                ArgumentCaptor.forClass(com.vanter.ember.billing.dto.BillVoidedMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/session/sess-1"), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("BILL_VOIDED");
        assertThat(captor.getValue().billId()).isEqualTo(1L);
    }

    @Test
    void voidBill_throwsWhenBillIsNotOpen() {
        Bill paid = openBill();
        paid.setStatus(BillStatus.PAID);
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> billingService.voidBill(1L, "reason", "waiter@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void voidBill_throwsWhenAConfirmedPaymentAlreadyExists() {
        Bill bill = openBill();
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndStatus(1L, PaymentStatus.CONFIRMED)).thenReturn(true);

        assertThatThrownBy(() -> billingService.voidBill(1L, "reason", "waiter@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void voidBill_throwsWhenBillNotFound() {
        when(billRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.voidBill(99L, "reason", "waiter@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
