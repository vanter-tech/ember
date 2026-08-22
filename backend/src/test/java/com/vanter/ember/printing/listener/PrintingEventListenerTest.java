package com.vanter.ember.printing.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.repository.PrintJobRepository;
import com.vanter.ember.printing.service.PrintDispatchService;
import com.vanter.ember.session.event.KitchenItemsConfirmed;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrintingEventListenerTest {

    @Mock SettingService settingService;
    @Mock PrintJobRepository printJobRepository;
    @Mock PrintDispatchService printDispatchService;
    @InjectMocks PrintingEventListener printingEventListener;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private RestaurantSettings settingsWith(boolean autoPrint) {
        SettingsPayload payload = new SettingsPayload();
        payload.getHardware().setAutoPrintTickets(autoPrint);
        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(payload);
        return settings;
    }

    @Test
    void onKitchenItemsConfirmed_autoPrintDisabled_doesNotCreateJob() {
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWith(false));

        printingEventListener.onKitchenItemsConfirmed(
                new KitchenItemsConfirmed(TENANT_ID, "session-1", 5, List.of()));

        verify(printJobRepository, never()).save(any());
        verify(printDispatchService, never()).dispatch(any());
    }

    @Test
    void onKitchenItemsConfirmed_autoPrintEnabled_createsAndDispatchesKitchenJob() {
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWith(true));
        when(printJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        OrderItem item = OrderItem.builder().id("i1").itemId(1L).name("Hamburguesa").build();

        printingEventListener.onKitchenItemsConfirmed(
                new KitchenItemsConfirmed(TENANT_ID, "session-1", 5, List.of(item)));

        ArgumentCaptor<PrintJob> jobCaptor = ArgumentCaptor.forClass(PrintJob.class);
        verify(printJobRepository).save(jobCaptor.capture());
        verify(printDispatchService).dispatch(jobCaptor.getValue());
        assertThat(jobCaptor.getValue().getRole().name()).isEqualTo("KITCHEN");
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(PrintJobStatus.PENDING);
        assertThat(jobCaptor.getValue().getSourceId()).isEqualTo("session-1");
        assertThat(jobCaptor.getValue().getPayload()).contains("Hamburguesa");
    }
}
