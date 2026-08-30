package com.vanter.ember.billing.listener;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillSplitStatus;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.billing.service.PaymentService;
import com.vanter.ember.session.event.ParticipantLeft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantLeftListenerTest {

    @Mock BillRepository billRepository;
    @Mock BillSplitRepository billSplitRepository;
    @Mock PaymentService paymentService;
    @InjectMocks ParticipantLeftListener listener;

    private ParticipantLeft event() {
        return new ParticipantLeft(UUID.randomUUID(), "sess-1", "user-1", "Alice");
    }

    private Bill bill() {
        return Bill.builder().id(7L).sessionId("sess-1").total(new BigDecimal("30.00"))
                .status(BillStatus.OPEN).build();
    }

    private BillSplit split(BillSplitStatus status) {
        return BillSplit.builder().id(1L).participantName("Alice")
                .amount(new BigDecimal("10.00")).status(status).build();
    }

    @Test
    void onParticipantLeft_redistributesWhenLeaverHasAnUnpaidSplit() {
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED))
                .thenReturn(Optional.of(bill()));
        when(billSplitRepository.findByBillIdAndParticipantName(7L, "Alice"))
                .thenReturn(Optional.of(split(BillSplitStatus.UNPAID)));

        listener.onParticipantLeft(event());

        verify(paymentService).redistributeSplit(7L, "Alice");
    }

    @Test
    void onParticipantLeft_doesNothingWhenSessionHasNoBill() {
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED))
                .thenReturn(Optional.empty());

        listener.onParticipantLeft(event());

        verify(paymentService, never()).redistributeSplit(anyLong(), anyString());
    }

    @Test
    void onParticipantLeft_doesNothingWhenLeaverSplitIsAlreadyPaid() {
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED))
                .thenReturn(Optional.of(bill()));
        when(billSplitRepository.findByBillIdAndParticipantName(7L, "Alice"))
                .thenReturn(Optional.of(split(BillSplitStatus.PAID)));

        listener.onParticipantLeft(event());

        verify(paymentService, never()).redistributeSplit(anyLong(), anyString());
    }

    @Test
    void onParticipantLeft_swallowsRedistributeFailure() {
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED))
                .thenReturn(Optional.of(bill()));
        when(billSplitRepository.findByBillIdAndParticipantName(7L, "Alice"))
                .thenReturn(Optional.of(split(BillSplitStatus.UNPAID)));
        when(paymentService.redistributeSplit(7L, "Alice"))
                .thenThrow(new IllegalStateException("no remaining participants"));

        listener.onParticipantLeft(event()); // must not propagate
    }
}
