package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.printing.dto.PrintJobAck;
import com.vanter.ember.printing.event.PrintAgentConnected;
import com.vanter.ember.printing.model.ConnectionType;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterConfig;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintJobRepository;
import com.vanter.ember.printing.repository.PrinterConfigRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class PrintDispatchServiceTest {

    @Mock PrinterConfigRepository printerConfigRepository;
    @Mock PrintJobRepository printJobRepository;
    @Mock PrintAgentConnectionRegistry connectionRegistry;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks PrintDispatchService printDispatchService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID AGENT_ID = UUID.randomUUID();

    private PrintJob kitchenJob() {
        return PrintJob.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).role(PrinterRole.KITCHEN)
                .sourceType(PrintJobSourceType.KITCHEN_TICKET).sourceId("session-1")
                .payload("{}").status(PrintJobStatus.PENDING).attempts(0)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private PrinterConfig kitchenPrinter() {
        return PrinterConfig.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).agentId(AGENT_ID)
                .role(PrinterRole.KITCHEN).connectionType(ConnectionType.NETWORK)
                .host("10.0.0.5").port(9100).label("Cocina 1").active(true).build();
    }

    @Test
    void dispatch_noPrintersConfigured_leavesJobPendingWithoutSending() {
        PrintJob job = kitchenJob();
        when(printerConfigRepository.findByTenantIdAndRoleAndActiveTrue(TENANT_ID, PrinterRole.KITCHEN))
                .thenReturn(List.of());

        printDispatchService.dispatch(job);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.PENDING);
    }

    @Test
    void dispatch_agentConnected_sendsToAgentTopicAndIncrementsAttempts() {
        PrintJob job = kitchenJob();
        PrinterConfig printer = kitchenPrinter();
        when(printerConfigRepository.findByTenantIdAndRoleAndActiveTrue(TENANT_ID, PrinterRole.KITCHEN))
                .thenReturn(List.of(printer));
        when(connectionRegistry.isConnected(AGENT_ID)).thenReturn(true);
        when(printJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        printDispatchService.dispatch(job);

        ArgumentCaptor<String> destination = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(destination.capture(), any(Object.class));
        assertThat(destination.getValue()).isEqualTo("/topic/print-agent/" + AGENT_ID);
        assertThat(job.getAttempts()).isEqualTo(1);
    }

    @Test
    void dispatch_agentNotConnected_leavesJobPendingWithoutSending() {
        PrintJob job = kitchenJob();
        when(printerConfigRepository.findByTenantIdAndRoleAndActiveTrue(TENANT_ID, PrinterRole.KITCHEN))
                .thenReturn(List.of(kitchenPrinter()));
        when(connectionRegistry.isConnected(AGENT_ID)).thenReturn(false);

        printDispatchService.dispatch(job);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.PENDING);
    }

    @Test
    void onPrintAgentConnected_flushesPendingJobsForThatAgent() {
        PrintJob job = kitchenJob();
        when(printJobRepository.findByStatus(PrintJobStatus.PENDING)).thenReturn(List.of(job));
        when(printerConfigRepository.findByTenantIdAndRoleAndActiveTrue(TENANT_ID, PrinterRole.KITCHEN))
                .thenReturn(List.of(kitchenPrinter()));
        when(connectionRegistry.isConnected(AGENT_ID)).thenReturn(true);
        when(printJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        printDispatchService.onPrintAgentConnected(new PrintAgentConnected(AGENT_ID));

        verify(messagingTemplate).convertAndSend("/topic/print-agent/" + AGENT_ID, new com.vanter.ember.printing.dto.PrintJobMessage(
                job.getId(), job.getRole().name(), job.getPayload()));
    }

    @Test
    void handleAck_printedResult_marksJobPrinted() {
        PrintJob job = kitchenJob();
        job.setStatus(PrintJobStatus.SENT);
        when(printJobRepository.findById(job.getId())).thenReturn(java.util.Optional.of(job));
        when(printJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        printDispatchService.handleAck(new PrintJobAck(job.getId(), UUID.randomUUID(), "PRINTED", null));

        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.PRINTED);
    }

    @Test
    void handleAck_errorResult_marksJobErrorWithMessage() {
        PrintJob job = kitchenJob();
        job.setStatus(PrintJobStatus.SENT);
        when(printJobRepository.findById(job.getId())).thenReturn(java.util.Optional.of(job));
        when(printJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        printDispatchService.handleAck(new PrintJobAck(job.getId(), UUID.randomUUID(), "ERROR", "Sin papel"));

        assertThat(job.getStatus()).isEqualTo(PrintJobStatus.ERROR);
        assertThat(job.getLastError()).isEqualTo("Sin papel");
    }
}
