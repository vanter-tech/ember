package com.vanter.ember.printing.repository;

import com.vanter.ember.printing.model.PrintAgent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintAgentRepository extends JpaRepository<PrintAgent, UUID> {

    List<PrintAgent> findByTenantId(UUID tenantId);
}
