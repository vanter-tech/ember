package com.vanter.ember.printing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.printing.model.ConnectionType;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrintAgentStatus;
import com.vanter.ember.printing.model.PrinterConfig;
import com.vanter.ember.printing.model.PrinterRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code @Transactional(NOT_SUPPORTED)} because Hibernate resolves the {@code @TenantId}
 * identifier once per session — {@code @DataJpaTest}'s shared per-test transaction would cache
 * whatever tenant was current when the session first opened, before this test ever binds
 * {@link #TENANT_ID}. Disabling it makes every repository call open its own session and
 * re-read {@link TenantContextHolder}, same technique as {@code AbstractTenantIsolationTest}.
 * Rows are committed rather than rolled back, so the test purges what it wrote afterward.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PrinterConfigRepositoryTest {

    @Autowired PrintAgentRepository printAgentRepository;
    @Autowired PrinterConfigRepository printerConfigRepository;

    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @AfterEach
    void purgeCommittedRows() {
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            printerConfigRepository.deleteAll(printerConfigRepository.findAll());
            printAgentRepository.deleteAll(printAgentRepository.findAll());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void findByTenantIdAndRoleAndActiveTrue_returnsOnlyActivePrintersOfThatRole() {
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            PrintAgent agent = printAgentRepository.save(PrintAgent.builder()
                    .id(UUID.randomUUID()).name("Agente Caja")
                    .apiKeyHash("hash").status(PrintAgentStatus.ACTIVE)
                    .createdAt(LocalDateTime.now()).build());

            printerConfigRepository.save(PrinterConfig.builder()
                    .id(UUID.randomUUID()).agentId(agent.getId())
                    .role(PrinterRole.KITCHEN).connectionType(ConnectionType.NETWORK)
                    .host("192.168.1.50").port(9100).label("Cocina 1").active(true).build());
            printerConfigRepository.save(PrinterConfig.builder()
                    .id(UUID.randomUUID()).agentId(agent.getId())
                    .role(PrinterRole.KITCHEN).connectionType(ConnectionType.NETWORK)
                    .host("192.168.1.51").port(9100).label("Cocina 2 (inactiva)").active(false).build());
            printerConfigRepository.save(PrinterConfig.builder()
                    .id(UUID.randomUUID()).agentId(agent.getId())
                    .role(PrinterRole.RECEIPT).connectionType(ConnectionType.USB)
                    .comPort("COM3").label("Caja").active(true).build());

            List<PrinterConfig> kitchenPrinters = printerConfigRepository
                    .findByTenantIdAndRoleAndActiveTrue(TENANT_ID, PrinterRole.KITCHEN);

            assertThat(kitchenPrinters).hasSize(1);
            assertThat(kitchenPrinters.get(0).getLabel()).isEqualTo("Cocina 1");
        } finally {
            TenantContextHolder.clear();
        }
    }
}
