package com.vanter.ember.printing.service;

import com.vanter.ember.printing.model.DrawerKickState;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintJobRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates cash-drawer kick jobs and reports how the last one went. */
@Service
@RequiredArgsConstructor
public class CashDrawerKickService {

    /** A SENT job with no ack after this long is treated as failed so the accountant can retry. */
    static final Duration STALE_AFTER = Duration.ofSeconds(15);

    private final PrintJobRepository printJobRepository;
    private final PrintDispatchService printDispatchService;
    private final PrintTargetResolver printTargetResolver;

    /** {@code sourceId} is the {@code CashDrawerEvent} id (plain reference, no FK). */
    @Transactional
    public PrintJob kick(UUID tenantId, String sourceId) {
        LocalDateTime now = LocalDateTime.now();
        PrintJob job = PrintJob.builder()
                .id(UUID.randomUUID())
                .role(PrinterRole.RECEIPT)
                .targetAgentId(printTargetResolver.resolveForCurrentRequest(tenantId, PrinterRole.RECEIPT).orElse(null))
                .sourceType(PrintJobSourceType.CASH_DRAWER_KICK)
                .sourceId(sourceId)
                .payload("{\"kick\":true}")
                .status(PrintJobStatus.PENDING)
                .attempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        // Same reason as PrintingEventListener: dispatch the MANAGED copy (tenantId filled).
        PrintJob saved = printJobRepository.saveAndFlush(job);
        printDispatchService.dispatch(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public DrawerKickState stateOf(UUID printJobId) {
        if (printJobId == null) {
            return DrawerKickState.NONE;
        }
        return printJobRepository.findById(printJobId).map(this::toState).orElse(DrawerKickState.FAILED);
    }

    /** The agent's (or dispatcher's) failure text for the kick, when it recorded one. */
    @Transactional(readOnly = true)
    public String errorOf(UUID printJobId) {
        if (printJobId == null) {
            return null;
        }
        return printJobRepository.findById(printJobId).map(PrintJob::getLastError).orElse(null);
    }

    private DrawerKickState toState(PrintJob job) {
        return switch (job.getStatus()) {
            case PRINTED -> DrawerKickState.OPENED;
            case ERROR, CANCELED -> DrawerKickState.FAILED;
            case PENDING, SENT -> job.getUpdatedAt().isBefore(LocalDateTime.now().minus(STALE_AFTER))
                    ? DrawerKickState.FAILED
                    : DrawerKickState.OPENING;
        };
    }
}
