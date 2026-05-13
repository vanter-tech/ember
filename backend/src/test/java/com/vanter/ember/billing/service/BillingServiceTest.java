package com.vanter.ember.billing.service;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.service.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock BillRepository billRepository;
    @Mock SessionService sessionService;
    @InjectMocks BillingService billingService;

    private Session sessionWithMixedItems() {
        return Session.builder()
                .id("sess-1").tableId(1L)
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
        when(billRepository.findBySessionId("sess-1")).thenReturn(Optional.empty());
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill bill = billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION);

        assertThat(bill.getTotal()).isEqualByComparingTo("22.50");
    }

    @Test
    void calculateBill_excludesPendingAndPreparingItems() {
        Session session = Session.builder().id("sess-1").tableId(1L)
                .items(new ArrayList<>(List.of(
                        OrderItem.builder().id("i-1").price(new BigDecimal("20.00"))
                                .status(OrderItemStatus.DELIVERED).build(),
                        OrderItem.builder().id("i-2").price(new BigDecimal("5.00"))
                                .status(OrderItemStatus.PREPARING).build(),
                        OrderItem.builder().id("i-3").price(new BigDecimal("3.00"))
                                .status(OrderItemStatus.PENDING).build()
                ))).build();
        when(sessionService.findById("sess-1")).thenReturn(session);
        when(billRepository.findBySessionId("sess-1")).thenReturn(Optional.empty());
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill bill = billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION);

        assertThat(bill.getTotal()).isEqualByComparingTo("20.00");
    }

    @Test
    void calculateBill_throwsWhenSessionHasNoBillableItems() {
        Session session = Session.builder().id("sess-1").tableId(1L)
                .items(new ArrayList<>(List.of(
                        OrderItem.builder().id("i-1").price(new BigDecimal("8.00"))
                                .status(OrderItemStatus.PENDING).build()
                ))).build();
        when(sessionService.findById("sess-1")).thenReturn(session);
        when(billRepository.findBySessionId("sess-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No billable items");
    }

    @Test
    void calculateBill_throwsWhenEmptyItemsList() {
        Session session = Session.builder().id("sess-1").tableId(1L)
                .items(new ArrayList<>()).build();
        when(sessionService.findById("sess-1")).thenReturn(session);
        when(billRepository.findBySessionId("sess-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No billable items");
    }

    @Test
    void calculateBill_throwsWhenSessionAlreadyBilled() {
        when(billRepository.findBySessionId("sess-1"))
                .thenReturn(Optional.of(Bill.builder().id(1L).build()));

        assertThatThrownBy(() -> billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already billed");
    }

    @Test
    void calculateBill_persistsBillWithOpenStatusAndCorrectFields() {
        when(sessionService.findById("sess-1")).thenReturn(sessionWithMixedItems());
        when(billRepository.findBySessionId("sess-1")).thenReturn(Optional.empty());
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
}
