package com.vanter.ember.printing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * A single physical printer owned by one {@link PrintAgent}. Multiple configs may share the
 * same {@code role} (spec §2.2/§2.3) — a {@code PrintJob} for that role is broadcast to every
 * active printer configured under it, grouped by the agent that owns each one.
 */
@Entity
@Table(name = "printer_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrinterConfig {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrinterRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false)
    private ConnectionType connectionType;

    private String host;

    private Integer port;

    @Column(name = "com_port")
    private String comPort;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean active;
}
