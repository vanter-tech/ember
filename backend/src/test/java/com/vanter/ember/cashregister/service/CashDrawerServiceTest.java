package com.vanter.ember.cashregister.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.cashregister.dto.CashDrawerEventResponse;
import com.vanter.ember.cashregister.dto.CashReceiptStatusResponse;
import com.vanter.ember.cashregister.model.CashDrawerEvent;
import com.vanter.ember.cashregister.model.CashDrawerEventStatus;
import com.vanter.ember.cashregister.model.CashDrawerEventType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.repository.CashDrawerEventRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.printing.model.DrawerKickState;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.service.CashDrawerKickService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashDrawerServiceTest {

    @Mock CashDrawerEventRepository repository;
    @Mock CashDrawerKickService kickService;
    @Mock CashShiftService cashShiftService;
    @Mock UserRepository userRepository;
    @Mock BillRepository billRepository;
    @Mock PaymentRepository paymentRepository;
    @InjectMocks CashDrawerService service;

    private static final UUID TENANT = UUID.randomUUID();

    private CashDrawerEvent pendingSale() {
        return CashDrawerEvent.builder().id(UUID.randomUUID()).type(CashDrawerEventType.CASH_SALE)
                .status(CashDrawerEventStatus.PENDING).paymentId(9L).cashShiftId(1L).tableNumber(3)
                .amount(new BigDecimal("20.00")).createdAt(LocalDateTime.now()).build();
    }

    private PrintJob job() {
        return PrintJob.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void receive_pendingSale_marksReceivedByWhoAndKicksTheDrawer() {
        CashDrawerEvent e = pendingSale();
        PrintJob j = job();
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        when(kickService.kick(TENANT, e.getId().toString())).thenReturn(j);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kickService.stateOf(j.getId())).thenReturn(DrawerKickState.OPENING);

        CashDrawerEventResponse r = service.receive(TENANT, e.getId(), "acc-1");

        assertThat(e.getStatus()).isEqualTo(CashDrawerEventStatus.RECEIVED);
        assertThat(e.getReceivedBy()).isEqualTo("acc-1");
        assertThat(e.getReceivedAt()).isNotNull();
        assertThat(e.getPrintJobId()).isEqualTo(j.getId());
        assertThat(r.drawer()).isEqualTo("OPENING");
    }

    @Test
    void receive_alreadyReceivedAndOpened_isRejectedWithoutKickingAgain() {
        CashDrawerEvent e = pendingSale();
        e.setStatus(CashDrawerEventStatus.RECEIVED);
        e.setPrintJobId(UUID.randomUUID());
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        when(kickService.stateOf(e.getPrintJobId())).thenReturn(DrawerKickState.OPENED);

        assertThatThrownBy(() -> service.receive(TENANT, e.getId(), "acc-1"))
                .isInstanceOf(IllegalStateException.class);
        verify(kickService, never()).kick(any(), any());
    }

    @Test
    void receive_receivedButLastKickFailed_retriesWithANewJobKeepingTheOriginalReceiver() {
        CashDrawerEvent e = pendingSale();
        e.setStatus(CashDrawerEventStatus.RECEIVED);
        e.setReceivedBy("acc-1");
        UUID oldJob = UUID.randomUUID();
        e.setPrintJobId(oldJob);
        PrintJob retry = job();
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        when(kickService.stateOf(oldJob)).thenReturn(DrawerKickState.FAILED);
        when(kickService.kick(TENANT, e.getId().toString())).thenReturn(retry);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kickService.stateOf(retry.getId())).thenReturn(DrawerKickState.OPENING);

        service.receive(TENANT, e.getId(), "acc-2");

        assertThat(e.getPrintJobId()).isEqualTo(retry.getId());
        assertThat(e.getReceivedBy()).isEqualTo("acc-1");
    }

    @Test
    void receive_unknownEvent_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.receive(TENANT, id, "acc-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void manualOpen_blankReason_isRejected() {
        assertThatThrownBy(() -> service.manualOpen(TENANT, "acc-1", true, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(kickService, never()).kick(any(), any());
    }

    @Test
    void manualOpen_accountantWithoutOpenShift_isRejected() {
        when(cashShiftService.findCurrentOpenShift(TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.manualOpen(TENANT, "acc-1", true, "cambio"))
                .isInstanceOf(IllegalStateException.class);
        verify(kickService, never()).kick(any(), any());
    }

    @Test
    void manualOpen_adminWithoutShift_isAllowedAndRecordsTheReason() {
        PrintJob j = job();
        when(cashShiftService.findCurrentOpenShift(TENANT)).thenReturn(Optional.empty());
        when(kickService.kick(any(), any())).thenReturn(j);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kickService.stateOf(j.getId())).thenReturn(DrawerKickState.OPENING);

        CashDrawerEventResponse r = service.manualOpen(TENANT, "admin-1", false, "retiro");

        assertThat(r.type()).isEqualTo("MANUAL");
        assertThat(r.reason()).isEqualTo("retiro");
        assertThat(r.status()).isEqualTo("RECEIVED");
    }

    @Test
    void listForPanel_usesTheOpenShiftAndMapsDrawerState() {
        CashShift shift = CashShift.builder().id(1L).build();
        CashDrawerEvent e = pendingSale();
        when(cashShiftService.findCurrentOpenShift(TENANT)).thenReturn(Optional.of(shift));
        when(repository.findForPanel(TENANT, 1L)).thenReturn(List.of(e));
        when(kickService.stateOf(null)).thenReturn(DrawerKickState.NONE);

        List<CashDrawerEventResponse> result = service.listForPanel(TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).drawer()).isEqualTo("NONE");
    }

    @Test
    void statusForSession_mapsEachCashPaymentToItsParticipantAndState() {
        Bill bill = Bill.builder().id(5L).build();
        Payment payment = Payment.builder().id(9L).participantName("Ana").build();
        CashDrawerEvent e = pendingSale();
        when(billRepository.findBySessionIdAndStatusNot("s1", BillStatus.VOIDED)).thenReturn(Optional.of(bill));
        when(paymentRepository.findByBillId(5L)).thenReturn(List.of(payment));
        when(repository.findByPaymentIdIn(java.util.Set.of(9L))).thenReturn(List.of(e));

        List<CashReceiptStatusResponse> result = service.statusForSession("s1");

        assertThat(result).containsExactly(new CashReceiptStatusResponse("Ana", "PENDING"));
    }

    @Test
    void statusForSession_isEmptyWithoutALiveBill() {
        when(billRepository.findBySessionIdAndStatusNot("s1", BillStatus.VOIDED)).thenReturn(Optional.empty());

        assertThat(service.statusForSession("s1")).isEmpty();
    }

    private CashDrawerEvent receivedWithJob(UUID jobId) {
        CashDrawerEvent e = pendingSale();
        e.setStatus(CashDrawerEventStatus.RECEIVED);
        e.setPrintJobId(jobId);
        return e;
    }

    @Test
    void skipDrawer_recordsWhoAndWhen_andReportsSkipped() {
        UUID jobId = UUID.randomUUID();
        CashDrawerEvent e = receivedWithJob(jobId);
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kickService.stateOf(jobId)).thenReturn(DrawerKickState.FAILED);

        CashDrawerEventResponse r = service.skipDrawer(e.getId(), "acc-1");

        assertThat(r.drawer()).isEqualTo("SKIPPED");
        assertThat(r.drawerError()).isNull();
        assertThat(e.getDrawerSkippedBy()).isEqualTo("acc-1");
        assertThat(e.getDrawerSkippedAt()).isNotNull();
    }

    @Test
    void skipDrawer_rejectsADrawerThatDidNotFail() {
        UUID jobId = UUID.randomUUID();
        CashDrawerEvent e = receivedWithJob(jobId);
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        when(kickService.stateOf(jobId)).thenReturn(DrawerKickState.OPENED);

        assertThatThrownBy(() -> service.skipDrawer(e.getId(), "acc-1")).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void skipDrawer_rejectsAPendingSale() {
        CashDrawerEvent e = pendingSale();
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.skipDrawer(e.getId(), "acc-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listForPanel_exposesTheFailureCauseOnlyWhenTheDrawerFailed() {
        UUID jobId = UUID.randomUUID();
        CashDrawerEvent e = receivedWithJob(jobId);
        when(cashShiftService.findCurrentOpenShift(TENANT)).thenReturn(Optional.empty());
        when(repository.findForPanel(TENANT, null)).thenReturn(List.of(e));
        when(kickService.stateOf(jobId)).thenReturn(DrawerKickState.FAILED);
        when(kickService.errorOf(jobId)).thenReturn("El agente de impresión no está conectado");

        CashDrawerEventResponse r = service.listForPanel(TENANT).get(0);

        assertThat(r.drawer()).isEqualTo("FAILED");
        assertThat(r.drawerError()).isEqualTo("El agente de impresión no está conectado");
    }

    @Test
    void receive_refusesToRetryASkippedDrawer() {
        UUID jobId = UUID.randomUUID();
        CashDrawerEvent e = receivedWithJob(jobId);
        e.setDrawerSkippedAt(LocalDateTime.now());
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.receive(TENANT, e.getId(), "acc-1")).isInstanceOf(IllegalStateException.class);
        verify(kickService, never()).kick(any(), any());
    }
}
