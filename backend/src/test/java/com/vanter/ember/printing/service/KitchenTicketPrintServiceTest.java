package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintJobRepository;
import com.vanter.ember.session.model.OrderItemStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KitchenTicketPrintServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock KitchenOrderRepository kitchenOrderRepository;
    @Mock PrintJobRepository printJobRepository;
    @Mock PrintDispatchService printDispatchService;
    @InjectMocks KitchenTicketPrintService service;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private KitchenOrder sampleOrder() {
        KitchenItem item = KitchenItem.builder()
                .itemId("order-item-1").name("Tacos").participantName("Alice")
                .status(OrderItemStatus.PENDING).updatedAt(LocalDateTime.now())
                .modifiers(List.of("Extra queso"))
                .build();
        return KitchenOrder.builder()
                .id("ko-1").tenantId(TENANT_ID).sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>(List.of(item))).build();
    }


    /** What Hibernate does with an assigned-id entity: MERGE, i.e. fill the tenant id on a COPY. */
    private static PrintJob mergedCopy(PrintJob given, UUID tenantId) {
        return PrintJob.builder()
                .id(given.getId()).tenantId(tenantId).role(given.getRole())
                .targetAgentId(given.getTargetAgentId()).sourceType(given.getSourceType())
                .sourceId(given.getSourceId()).payload(given.getPayload()).status(given.getStatus())
                .attempts(given.getAttempts()).createdAt(given.getCreatedAt()).updatedAt(given.getUpdatedAt())
                .build();
    }

    @Test
    void enqueue_dispatchesTheInstanceSaveAndFlushReturns_becauseOnlyThatOneCarriesTheTenantId() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID)).thenReturn(Optional.of(sampleOrder()));
        when(printJobRepository.saveAndFlush(any())).thenAnswer(i -> mergedCopy(i.getArgument(0), TENANT_ID));

        PrintJob returned = service.enqueue("ko-1");

        org.mockito.ArgumentCaptor<PrintJob> dispatched = org.mockito.ArgumentCaptor.forClass(PrintJob.class);
        verify(printDispatchService).dispatch(dispatched.capture());
        assertThat(dispatched.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(returned).isSameAs(dispatched.getValue());
    }

    @Test
    void enqueue_buildsPendingKitchenTicketJob() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID))
                .thenReturn(Optional.of(sampleOrder()));
        when(printJobRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        PrintJob job = service.enqueue("ko-1");

        assertThat(job.getRole()).isEqualTo(PrinterRole.KITCHEN);
        assertThat(job.getSourceType()).isEqualTo(PrintJobSourceType.KITCHEN_TICKET);
        assertThat(job.getSourceId()).isEqualTo("sess-1");
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.PENDING);
        assertThat(job.getPayload()).contains("Mesa 5", "Tacos", "Extra queso");
        verify(printJobRepository).saveAndFlush(job);
        verify(printDispatchService).dispatch(job);
    }

    @Test
    void enqueue_throwsNotFound_whenOrderMissing() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(kitchenOrderRepository.findByIdAndTenantId("ko-999", TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enqueue("ko-999")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(printDispatchService);
    }
}
