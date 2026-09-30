package com.vanter.ember.cashregister.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.billing.event.PhysicalPaymentRegistered;
import com.vanter.ember.cashregister.model.CashDrawerEvent;
import com.vanter.ember.cashregister.model.CashDrawerEventStatus;
import com.vanter.ember.cashregister.model.CashDrawerEventType;
import com.vanter.ember.cashregister.repository.CashDrawerEventRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.service.SessionService;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashDrawerEventListenerTest {

    @Mock CashDrawerEventRepository repository;
    @Mock SessionService sessionService;
    @Mock DiningTableRepository diningTableRepository;
    @InjectMocks CashDrawerEventListener listener;

    private static final UUID TENANT = UUID.randomUUID();

    private PhysicalPaymentRegistered event() {
        return new PhysicalPaymentRegistered(TENANT, 7L, 3L, "sess-1", new BigDecimal("12.50"), 5L, "waiter-1");
    }

    @Test
    void onPhysicalPayment_createsAPendingCashSaleWithTheTableNumber() {
        Session session = new Session();
        session.setTableId(UUID.randomUUID());
        DiningTables table = new DiningTables();
        table.setTableNumber(4);
        when(sessionService.findById("sess-1")).thenReturn(session);
        when(diningTableRepository.findByRestaurantIdAndIdIn(any(), any())).thenReturn(List.of(table));

        listener.onPhysicalPayment(event());

        ArgumentCaptor<CashDrawerEvent> saved = ArgumentCaptor.forClass(CashDrawerEvent.class);
        verify(repository).save(saved.capture());
        CashDrawerEvent e = saved.getValue();
        assertThat(e.getType()).isEqualTo(CashDrawerEventType.CASH_SALE);
        assertThat(e.getStatus()).isEqualTo(CashDrawerEventStatus.PENDING);
        assertThat(e.getPaymentId()).isEqualTo(7L);
        assertThat(e.getCashShiftId()).isEqualTo(5L);
        assertThat(e.getTableNumber()).isEqualTo(4);
        assertThat(e.getAmount()).isEqualByComparingTo("12.50");
        assertThat(e.getCreatedBy()).isEqualTo("waiter-1");
    }

    @Test
    void onPhysicalPayment_tableLookupFailure_stillRecordsThePendingReceipt() {
        when(sessionService.findById("sess-1")).thenThrow(new IllegalStateException("gone"));

        listener.onPhysicalPayment(event());

        ArgumentCaptor<CashDrawerEvent> saved = ArgumentCaptor.forClass(CashDrawerEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTableNumber()).isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(CashDrawerEventStatus.PENDING);
    }
}
