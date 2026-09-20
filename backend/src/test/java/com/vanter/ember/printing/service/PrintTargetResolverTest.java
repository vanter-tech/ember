package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vanter.ember.printing.model.ConnectionType;
import com.vanter.ember.printing.model.PrinterConfig;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrinterConfigRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PrintTargetResolverTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String CAJA1_IP = "203.0.113.21";

    private PrintAgentConnectionRegistry registry;
    private PrinterConfigRepository printers;
    private PrintTargetResolver resolver;
    private UUID caja1;

    @BeforeEach
    void setUp() {
        registry = new PrintAgentConnectionRegistry();
        printers = mock(PrinterConfigRepository.class);
        resolver = new PrintTargetResolver(registry, printers, true);
        caja1 = UUID.randomUUID();
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private PrinterConfig receiptPrinterOf(UUID agentId) {
        return PrinterConfig.builder()
                .id(UUID.randomUUID()).tenantId(TENANT).agentId(agentId).role(PrinterRole.RECEIPT)
                .connectionType(ConnectionType.NETWORK).host("10.0.0.5").port(9100).active(true).build();
    }

    private void requestFrom(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void routesToTheAgentConnectedFromTheRequesterAddress() {
        registry.markConnected(caja1, "s1", CAJA1_IP);
        UUID caja2 = UUID.randomUUID();
        registry.markConnected(caja2, "s2", "203.0.113.22");
        when(printers.findByTenantIdAndRoleAndActiveTrue(TENANT, PrinterRole.RECEIPT))
                .thenReturn(List.of(receiptPrinterOf(caja1), receiptPrinterOf(caja2)));
        requestFrom(CAJA1_IP);

        assertThat(resolver.resolveForCurrentRequest(TENANT, PrinterRole.RECEIPT)).contains(caja1);
    }

    @Test
    void aBrowserOnTheHubsOwnPc_reachesTheAgentOnThatSamePc() {
        registry.markConnected(caja1, "s1", "127.0.0.1");
        when(printers.findByTenantIdAndRoleAndActiveTrue(TENANT, PrinterRole.RECEIPT))
                .thenReturn(List.of(receiptPrinterOf(caja1)));
        requestFrom("0:0:0:0:0:0:0:1");

        assertThat(resolver.resolveForCurrentRequest(TENANT, PrinterRole.RECEIPT)).contains(caja1);
    }

    @Test
    void aDeviceWithNoAgent_fallsBackToTheOldBehavior() {
        registry.markConnected(caja1, "s1", CAJA1_IP);
        requestFrom("203.0.113.77");

        assertThat(resolver.resolveForCurrentRequest(TENANT, PrinterRole.RECEIPT)).isEmpty();
    }

    @Test
    void anAgentWithoutAnActivePrinterForTheRole_isNotATarget() {
        registry.markConnected(caja1, "s1", CAJA1_IP);
        when(printers.findByTenantIdAndRoleAndActiveTrue(TENANT, PrinterRole.RECEIPT))
                .thenReturn(List.of(receiptPrinterOf(UUID.randomUUID())));
        requestFrom(CAJA1_IP);

        assertThat(resolver.resolveForCurrentRequest(TENANT, PrinterRole.RECEIPT)).isEmpty();
    }

    @Test
    void twoAgentsOnOnePc_isAmbiguous_soNoRouting() {
        registry.markConnected(caja1, "s1", CAJA1_IP);
        registry.markConnected(UUID.randomUUID(), "s2", CAJA1_IP);
        requestFrom(CAJA1_IP);

        assertThat(resolver.resolveForCurrentRequest(TENANT, PrinterRole.RECEIPT)).isEmpty();
    }

    @Test
    void disabledByConfig_neverRoutes_evenWhenAnAgentMatches() {
        resolver = new PrintTargetResolver(registry, printers, false);
        registry.markConnected(caja1, "s1", CAJA1_IP);
        requestFrom(CAJA1_IP);

        assertThat(resolver.resolveForCurrentRequest(TENANT, PrinterRole.RECEIPT)).isEmpty();
    }

    @Test
    void outsideAWebRequest_hasNoRequester_soNoRouting() {
        registry.markConnected(caja1, "s1", CAJA1_IP);

        assertThat(resolver.resolveForCurrentRequest(TENANT, PrinterRole.RECEIPT)).isEmpty();
    }
}
