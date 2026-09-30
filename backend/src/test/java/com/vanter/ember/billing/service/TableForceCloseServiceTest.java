package com.vanter.ember.billing.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.service.SessionService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TableForceCloseServiceTest {

    @Mock SessionService sessionService;
    @Mock BillRepository billRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock BillingService billingService;
    @InjectMocks TableForceCloseService service;

    private Session session(SessionStatus status) {
        return Session.builder().id("sess-1").status(status).build();
    }

    private Bill bill(BillStatus status) {
        return Bill.builder().id(7L).sessionId("sess-1").status(status).build();
    }

    @Test
    void voidsTheOpenBill_thenClosesTheTable_recordingWhoAndWhy() {
        when(sessionService.findById("sess-1")).thenReturn(session(SessionStatus.OPEN));
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED))
                .thenReturn(Optional.of(bill(BillStatus.OPEN)));
        when(paymentRepository.existsByBillIdAndStatus(7L, PaymentStatus.CONFIRMED)).thenReturn(false);

        service.forceClose("sess-1", "admin@test.com", "cliente se fue");

        InOrder order = inOrder(billingService, sessionService);
        order.verify(billingService).voidBill(7L, "Cerrada por administrador: cliente se fue", "admin@test.com");
        order.verify(sessionService).closeByAdmin("sess-1", "admin@test.com", "cliente se fue");
    }

    @Test
    void aTableWithNoBillYet_justCloses() {
        when(sessionService.findById("sess-1")).thenReturn(session(SessionStatus.OPEN));
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED)).thenReturn(Optional.empty());

        service.forceClose("sess-1", "admin@test.com", "mesa abandonada");

        verify(billingService, never()).voidBill(anyLong(), anyString(), anyString());
        verify(sessionService).closeByAdmin("sess-1", "admin@test.com", "mesa abandonada");
    }

    @Test
    void refusesWhenTheBillAlreadyHasAConfirmedPayment_andChangesNothing() {
        when(sessionService.findById("sess-1")).thenReturn(session(SessionStatus.OPEN));
        when(billRepository.findBySessionIdAndStatusNot("sess-1", BillStatus.VOIDED))
                .thenReturn(Optional.of(bill(BillStatus.OPEN)));
        when(paymentRepository.existsByBillIdAndStatus(7L, PaymentStatus.CONFIRMED)).thenReturn(true);

        assertThatThrownBy(() -> service.forceClose("sess-1", "admin@test.com", "x"))
                .isInstanceOf(IllegalStateException.class);
        verify(billingService, never()).voidBill(anyLong(), anyString(), anyString());
        verify(sessionService, never()).closeByAdmin(anyString(), anyString(), anyString());
    }

    @Test
    void refusesAnAlreadyClosedTable() {
        when(sessionService.findById("sess-1")).thenReturn(session(SessionStatus.CLOSED));

        assertThatThrownBy(() -> service.forceClose("sess-1", "admin@test.com", "x"))
                .isInstanceOf(IllegalStateException.class);
        verify(sessionService, never()).closeByAdmin(any(), any(), any());
    }

    @Test
    void aTableOfAnotherRestaurantIsNotFound_andNothingIsTouched() {
        when(sessionService.findById("sess-x")).thenThrow(new ResourceNotFoundException("Session not found: sess-x"));

        assertThatThrownBy(() -> service.forceClose("sess-x", "admin@test.com", "x"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(billingService, never()).voidBill(anyLong(), anyString(), anyString());
        verify(sessionService, never()).closeByAdmin(any(), any(), any());
    }
}
