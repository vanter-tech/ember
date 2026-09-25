package com.vanter.ember.printing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.logo.TicketLogoService;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PrintAgentLogoControllerTest {

    private final UUID agentId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private JwtService jwtService;
    private PrintAgentRepository agentRepository;
    private TicketLogoService logoService;
    private SettingService settingService;
    private PrintAgentLogoController controller;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        agentRepository = mock(PrintAgentRepository.class);
        logoService = mock(TicketLogoService.class);
        settingService = mock(SettingService.class);
        controller = new PrintAgentLogoController(jwtService, agentRepository, logoService, settingService);

        when(jwtService.extractSubject("tok")).thenReturn(agentId.toString());
        PrintAgent agent = new PrintAgent();
        agent.setTenantId(tenantId);
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(new SettingsPayload());
        when(settingService.getSettings(tenantId)).thenAnswer(inv -> {
            // Settings are @TenantId-scoped: the tenant must be bound while they are read.
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(tenantId);
            return settings;
        });
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsThePngWithAnEtagAndReleasesTheTenantContext() {
        when(logoService.loadBitonal(eq(tenantId), any())).thenReturn(Optional.of(new byte[] {1, 2, 3}));

        ResponseEntity<byte[]> response = controller.logo("Bearer tok", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(1, 2, 3);
        assertThat(response.getHeaders().getETag()).isNotBlank();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void answers304WhenTheEtagMatches() {
        when(logoService.loadBitonal(eq(tenantId), any())).thenReturn(Optional.of(new byte[] {1, 2, 3}));
        String etag = controller.logo("Bearer tok", null).getHeaders().getETag();

        ResponseEntity<byte[]> response = controller.logo("Bearer tok", etag);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void is404WhenTheRestaurantHasNoLogo() {
        when(logoService.loadBitonal(eq(tenantId), any())).thenReturn(Optional.empty());

        assertThat(controller.logo("Bearer tok", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void is404ForAnUnknownAgent() {
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());

        assertThat(controller.logo("Bearer tok", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
