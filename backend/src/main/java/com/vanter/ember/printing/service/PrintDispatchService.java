package com.vanter.ember.printing.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.printing.dto.PrintJobAck;
import com.vanter.ember.printing.dto.PrintJobMessage;
import com.vanter.ember.printing.event.PrintAgentConnected;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterConfig;
import com.vanter.ember.printing.repository.PrintJobRepository;
import com.vanter.ember.printing.repository.PrinterConfigRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a {@link PrintJob}'s target printers, groups them by owning agent, and pushes to
 * whichever agents currently hold a live {@code /ws/print-agent} session. A job with no
 * configured printers, or whose agent(s) are offline, stays {@code PENDING} — not an error
 * (spec §3.3).
 */
@Service
@RequiredArgsConstructor
public class PrintDispatchService {

    private final PrinterConfigRepository printerConfigRepository;
    private final PrintJobRepository printJobRepository;
    private final PrintAgentConnectionRegistry connectionRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void dispatch(PrintJob job) {
        List<PrinterConfig> printers =
                printerConfigRepository.findByTenantIdAndRoleAndActiveTrue(job.getTenantId(), job.getRole());
        if (printers.isEmpty()) {
            printJobRepository.save(job);
            return;
        }

        Map<UUID, List<PrinterConfig>> byAgent =
                printers.stream().collect(Collectors.groupingBy(PrinterConfig::getAgentId));

        boolean sentToAny = false;
        for (UUID agentId : byAgent.keySet()) {
            if (connectionRegistry.isConnected(agentId)) {
                sendTo(agentId, job);
                sentToAny = true;
            }
        }

        job.setAttempts(job.getAttempts() + 1);
        job.setStatus(sentToAny ? PrintJobStatus.SENT : PrintJobStatus.PENDING);
        job.setUpdatedAt(LocalDateTime.now());
        printJobRepository.save(job);
    }

    /**
     * Reacts to {@link PrintAgentConnected} (published by {@code PrintAgentChannelInterceptor}
     * on CONNECT) rather than being called directly — a direct dependency there would close a
     * circular bean cycle back through {@code SimpMessagingTemplate}. See {@link
     * PrintAgentConnected}'s javadoc for the full explanation.
     */
    @EventListener
    @Transactional
    public void onPrintAgentConnected(PrintAgentConnected event) {
        flushPendingFor(event.agentId());
    }

    public void flushPendingFor(UUID agentId) {
        printJobRepository.findByStatus(PrintJobStatus.PENDING).forEach(this::dispatch);
    }

    @Transactional
    public void handleAck(PrintJobAck ack) {
        printJobRepository.findById(ack.jobId()).ifPresent(job -> {
            if ("PRINTED".equals(ack.result())) {
                job.setStatus(PrintJobStatus.PRINTED);
            } else {
                job.setStatus(PrintJobStatus.ERROR);
                job.setLastError(ack.error());
            }
            job.setUpdatedAt(LocalDateTime.now());
            printJobRepository.save(job);
        });
    }

    @Transactional
    public void retry(UUID tenantId, UUID jobId) {
        PrintJob job = printJobRepository.findById(jobId)
                .filter(j -> j.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Print job not found: " + jobId));
        dispatch(job);
    }

    private void sendTo(UUID agentId, PrintJob job) {
        messagingTemplate.convertAndSend(
                "/topic/print-agent/" + agentId,
                new PrintJobMessage(job.getId(), job.getRole().name(), job.getPayload()));
    }
}
