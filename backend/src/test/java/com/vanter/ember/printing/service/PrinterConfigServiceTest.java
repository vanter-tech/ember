package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.printing.dto.CreatePrinterConfigRequest;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrintAgentStatus;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import com.vanter.ember.printing.repository.PrinterConfigRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrinterConfigServiceTest {

    @Mock PrinterConfigRepository printerConfigRepository;
    @Mock PrintAgentRepository printAgentRepository;
    @InjectMocks PrinterConfigService printerConfigService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void create_underAgentFromDifferentTenant_throwsResourceNotFound() {
        UUID agentId = UUID.randomUUID();
        PrintAgent otherTenantAgent = PrintAgent.builder()
                .id(agentId).tenantId(UUID.randomUUID()).name("Otro")
                .apiKeyHash("h").status(PrintAgentStatus.ACTIVE).createdAt(LocalDateTime.now()).build();
        when(printAgentRepository.findById(agentId)).thenReturn(Optional.of(otherTenantAgent));

        assertThatThrownBy(() -> printerConfigService.create(TENANT_ID, agentId,
                new CreatePrinterConfigRequest("KITCHEN", "NETWORK", "10.0.0.5", 9100, null, "Cocina 1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_validAgent_savesWithRoleAndConnectionType() {
        UUID agentId = UUID.randomUUID();
        PrintAgent agent = PrintAgent.builder()
                .id(agentId).tenantId(TENANT_ID).name("Agente Cocina")
                .apiKeyHash("h").status(PrintAgentStatus.ACTIVE).createdAt(LocalDateTime.now()).build();
        when(printAgentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(printerConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = printerConfigService.create(TENANT_ID, agentId,
                new CreatePrinterConfigRequest("KITCHEN", "NETWORK", "10.0.0.5", 9100, null, "Cocina 1"));

        assertThat(response.role()).isEqualTo("KITCHEN");
        assertThat(response.connectionType()).isEqualTo("NETWORK");
        assertThat(response.label()).isEqualTo("Cocina 1");
    }
}
