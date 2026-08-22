package com.vanter.ember.printing.repository;

import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {

    Page<PrintJob> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<PrintJob> findByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, PrintJobStatus status, Pageable pageable);

    List<PrintJob> findByStatus(PrintJobStatus status);
}
