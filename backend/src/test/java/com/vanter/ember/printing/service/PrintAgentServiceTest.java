package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.printing.dto.CreatedPrintAgentResponse;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrintAgentStatus;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PrintAgentServiceTest {

    @Mock PrintAgentRepository printAgentRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks PrintAgentService printAgentService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void create_savesHashedKeyAndReturnsPlaintextOnce() {
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hashed-value");
        when(printAgentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreatedPrintAgentResponse response = printAgentService.create(TENANT_ID, "Agente Caja");

        assertThat(response.name()).isEqualTo("Agente Caja");
        assertThat(response.apiKey()).isNotBlank();
        assertThat(response.apiKey().length()).isGreaterThanOrEqualTo(32);
    }

    @Test
    void revoke_unknownAgent_throwsResourceNotFound() {
        UUID agentId = UUID.randomUUID();
        when(printAgentRepository.findById(agentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> printAgentService.revoke(TENANT_ID, agentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revoke_agentFromDifferentTenant_throwsResourceNotFound() {
        UUID agentId = UUID.randomUUID();
        PrintAgent other = PrintAgent.builder()
                .id(agentId).tenantId(UUID.randomUUID()).name("Otro")
                .apiKeyHash("h").status(PrintAgentStatus.ACTIVE).createdAt(LocalDateTime.now()).build();
        when(printAgentRepository.findById(agentId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> printAgentService.revoke(TENANT_ID, agentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
