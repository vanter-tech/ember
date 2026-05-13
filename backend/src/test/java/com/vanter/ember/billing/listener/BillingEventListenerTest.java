package com.vanter.ember.billing.listener;

import com.vanter.ember.billing.dto.BillReadyMessage;
import com.vanter.ember.billing.event.BillingRequested;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.service.BillingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingEventListenerTest {

    @Mock BillingService billingService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks BillingEventListener billingEventListener;

    private Bill sampleBill() {
        return Bill.builder()
                .id(1L).sessionId("sess-1").total(new BigDecimal("22.50"))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now()).build();
    }

    private List<BillSplit> sampleSplits(Bill bill) {
        return List.of(
                BillSplit.builder().bill(bill).participantName("Alice")
                        .amount(new BigDecimal("12.50")).paid(false).build(),
                BillSplit.builder().bill(bill).participantName("Bob")
                        .amount(new BigDecimal("10.00")).paid(false).build()
        );
    }

    @Test
    void handleBillingRequested_byConsumption_callsCalculateBillAndSplitByConsumption() {
        Bill bill = sampleBill();
        when(billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION)).thenReturn(bill);
        when(billingService.splitByConsumption(1L)).thenReturn(sampleSplits(bill));

        billingEventListener.handleBillingRequested(
                new BillingRequested("sess-1", SplitMethod.BY_CONSUMPTION, 2));

        verify(billingService).calculateBill("sess-1", SplitMethod.BY_CONSUMPTION);
        verify(billingService).splitByConsumption(1L);
    }

    @Test
    void handleBillingRequested_equalParts_callsCalculateBillAndSplitEqually() {
        Bill bill = sampleBill();
        when(billingService.calculateBill("sess-1", SplitMethod.EQUAL_PARTS)).thenReturn(bill);
        when(billingService.splitEqually(1L, 2)).thenReturn(sampleSplits(bill));

        billingEventListener.handleBillingRequested(
                new BillingRequested("sess-1", SplitMethod.EQUAL_PARTS, 2));

        verify(billingService).calculateBill("sess-1", SplitMethod.EQUAL_PARTS);
        verify(billingService).splitEqually(1L, 2);
    }

    @Test
    void handleBillingRequested_broadcastsBillReadyToSessionTopic() {
        Bill bill = sampleBill();
        when(billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION)).thenReturn(bill);
        when(billingService.splitByConsumption(1L)).thenReturn(sampleSplits(bill));

        billingEventListener.handleBillingRequested(
                new BillingRequested("sess-1", SplitMethod.BY_CONSUMPTION, 2));

        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/session/sess-1"),
                org.mockito.ArgumentMatchers.any(BillReadyMessage.class));
    }

    @Test
    void handleBillingRequested_payloadContainsBillIdAndSplits() {
        Bill bill = sampleBill();
        when(billingService.calculateBill("sess-1", SplitMethod.BY_CONSUMPTION)).thenReturn(bill);
        when(billingService.splitByConsumption(1L)).thenReturn(sampleSplits(bill));

        billingEventListener.handleBillingRequested(
                new BillingRequested("sess-1", SplitMethod.BY_CONSUMPTION, 2));

        ArgumentCaptor<BillReadyMessage> captor = ArgumentCaptor.forClass(BillReadyMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/session/sess-1"), captor.capture());
        BillReadyMessage msg = captor.getValue();
        assertThat(msg.type()).isEqualTo("BILL_READY");
        assertThat(msg.billId()).isEqualTo(1L);
        assertThat(msg.total()).isEqualByComparingTo("22.50");
        assertThat(msg.splits()).hasSize(2);
    }
}
