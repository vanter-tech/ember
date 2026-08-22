package com.vanter.ember.printing.service;

import com.vanter.ember.printing.dto.PrintJobResponse;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.repository.PrintJobRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrintJobQueryService {

    private final PrintJobRepository printJobRepository;

    public Page<PrintJobResponse> list(UUID tenantId, PrintJobStatus status, Pageable pageable) {
        Page<PrintJob> page = status == null
                ? printJobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                : printJobRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable);
        return page.map(this::toResponse);
    }

    private PrintJobResponse toResponse(PrintJob job) {
        return new PrintJobResponse(
                job.getId(), job.getRole().name(), job.getSourceType().name(), job.getSourceId(),
                job.getStatus().name(), job.getAttempts(), job.getLastError(), job.getCreatedAt());
    }
}
