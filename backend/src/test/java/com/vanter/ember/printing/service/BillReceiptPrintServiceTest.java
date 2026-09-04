package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.printing.exception.BillNotPaidException;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintJobRepository;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillReceiptPrintServiceTest {

    @Mock BillRepository billRepository;
    @Mock SettingService settingService;
    @Mock ReceiptRenderer receiptRenderer;
    @Mock PrintJobRepository printJobRepository;
    @Mock PrintDispatchService printDispatchService;
    @InjectMocks BillReceiptPrintService service;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private Bill bill(BillStatus status) {
        Bill b = new Bill();
        b.setId(42L);
        b.setStatus(status);
        return b;
    }

    @Test
    void enqueue_buildsPendingReceiptJob_whenBillPaid() {
        TenantContextHolder.setTenantId(UUID.randomUUID());
        when(billRepository.findById(42L)).thenReturn(Optional.of(bill(BillStatus.PAID)));
        RestaurantSettings s = mock(RestaurantSettings.class);
        when(s.getPayload()).thenReturn(new SettingsPayload());
        when(settingService.getSettings(any())).thenReturn(s);
        when(receiptRenderer.render(eq(42L), any())).thenReturn("Bill #42\n");
        when(printJobRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        PrintJob job = service.enqueue(42L);

        assertThat(job.getRole()).isEqualTo(PrinterRole.RECEIPT);
        assertThat(job.getSourceType()).isEqualTo(PrintJobSourceType.BILL_RECEIPT);
        assertThat(job.getSourceId()).isEqualTo("42");
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.PENDING);
        assertThat(job.getPayload()).isEqualTo("Bill #42\n");
        verify(printJobRepository).saveAndFlush(job);
        verify(printDispatchService).dispatch(job);
    }

    @Test
    void enqueue_throwsBillNotPaid_whenBillOpen() {
        when(billRepository.findById(42L)).thenReturn(Optional.of(bill(BillStatus.OPEN)));

        assertThatThrownBy(() -> service.enqueue(42L)).isInstanceOf(BillNotPaidException.class);
        verifyNoInteractions(printDispatchService);
    }

    @Test
    void enqueue_throwsNotFound_whenBillMissing() {
        when(billRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enqueue(99L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(printDispatchService);
    }
}
