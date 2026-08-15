package com.vanter.ember.platform.repository;

import com.vanter.ember.platform.model.PlatformAuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, UUID> {}
