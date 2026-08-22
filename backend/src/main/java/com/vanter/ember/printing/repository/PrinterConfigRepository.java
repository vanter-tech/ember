package com.vanter.ember.printing.repository;

import com.vanter.ember.printing.model.PrinterConfig;
import com.vanter.ember.printing.model.PrinterRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrinterConfigRepository extends JpaRepository<PrinterConfig, UUID> {

    List<PrinterConfig> findByTenantIdAndRoleAndActiveTrue(UUID tenantId, PrinterRole role);

    List<PrinterConfig> findByAgentId(UUID agentId);

    List<PrinterConfig> findByAgentIdAndActiveTrue(UUID agentId);
}
