package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.printing.model.DrawerKickState;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintJobRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashDrawerKickServiceTest {

    @Mock PrintJobRepository printJobRepository;
    @Mock PrintDispatchService printDispatchService;
    @Mock PrintTargetResolver printTargetResolver;
    @InjectMocks CashDrawerKickService service;

    private static final UUID TENANT = UUID.randomUUID();

    private PrintJob jobWith(PrintJobStatus status, LocalDateTime updatedAt) {
        return PrintJob.builder().id(UUID.randomUUID()).tenantId(TENANT).role(PrinterRole.RECEIPT)
                .sourceType(PrintJobSourceType.CASH_DRAWER_KICK).sourceId("e").payload("{}")
                .status(status).attempts(1).createdAt(updatedAt).updatedAt(updatedAt).build();
    }

    @Test
    void kick_savesAReceiptRoleKickJobAndDispatchesTheSavedCopy() {
        when(printTargetResolver.resolveForCurrentRequest(TENANT, PrinterRole.RECEIPT))
                .thenReturn(Optional.empty());
        when(printJobRepository.saveAndFlush(any(PrintJob.class))).thenAnswer(inv -> inv.getArgument(0));

        PrintJob job = service.kick(TENANT, "evt-1");

        assertThat(job.getRole()).isEqualTo(PrinterRole.RECEIPT);
        assertThat(job.getSourceType()).isEqualTo(PrintJobSourceType.CASH_DRAWER_KICK);
        assertThat(job.getSourceId()).isEqualTo("evt-1");
        assertThat(job.getPayload()).contains("kick");
        assertThat(job.getTargetAgentId()).isNull();
        verify(printDispatchService).dispatch(job);
    }

    @Test
    void kick_usesTheResolvedAgentWhenSourceIpRoutingResolvesOne() {
        UUID agent = UUID.randomUUID();
        when(printTargetResolver.resolveForCurrentRequest(TENANT, PrinterRole.RECEIPT))
                .thenReturn(Optional.of(agent));
        when(printJobRepository.saveAndFlush(any(PrintJob.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.kick(TENANT, "evt-1").getTargetAgentId()).isEqualTo(agent);
    }

    @Test
    void stateOf_mapsJobStatusToDrawerState() {
        UUID printed = UUID.randomUUID(), failed = UUID.randomUUID(), sending = UUID.randomUUID(),
                stale = UUID.randomUUID(), missing = UUID.randomUUID();
        when(printJobRepository.findById(printed))
                .thenReturn(Optional.of(jobWith(PrintJobStatus.PRINTED, LocalDateTime.now())));
        when(printJobRepository.findById(failed))
                .thenReturn(Optional.of(jobWith(PrintJobStatus.ERROR, LocalDateTime.now())));
        when(printJobRepository.findById(sending))
                .thenReturn(Optional.of(jobWith(PrintJobStatus.SENT, LocalDateTime.now())));
        when(printJobRepository.findById(stale))
                .thenReturn(Optional.of(jobWith(PrintJobStatus.SENT, LocalDateTime.now().minusMinutes(1))));
        when(printJobRepository.findById(missing)).thenReturn(Optional.empty());

        assertThat(service.stateOf(null)).isEqualTo(DrawerKickState.NONE);
        assertThat(service.stateOf(printed)).isEqualTo(DrawerKickState.OPENED);
        assertThat(service.stateOf(failed)).isEqualTo(DrawerKickState.FAILED);
        assertThat(service.stateOf(sending)).isEqualTo(DrawerKickState.OPENING);
        assertThat(service.stateOf(stale)).isEqualTo(DrawerKickState.FAILED);
        assertThat(service.stateOf(missing)).isEqualTo(DrawerKickState.FAILED);
    }
}
