package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vanter.ember.printing.dto.PairResponse;
import com.vanter.ember.printing.dto.PairingCodeResponse;
import com.vanter.ember.printing.model.DiscoveredPrinter;
import com.vanter.ember.printing.model.PairingCode;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrintAgentStatus;
import com.vanter.ember.printing.repository.PairingCodeRepository;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PrintAgentPairingServiceTest {

    @Mock PrintAgentRepository printAgentRepository;
    @Mock PairingCodeRepository pairingCodeRepository;
    @Mock PrintAgentConnectionRegistry connectionRegistry;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private PrintAgentService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID AGENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PrintAgentService(
                printAgentRepository, passwordEncoder, connectionRegistry, pairingCodeRepository);
        ReflectionTestUtils.setField(service, "agentBackendBaseUrl", "https://api.example/v1");
    }

    private PrintAgent activeAgent() {
        return PrintAgent.builder()
                .id(AGENT_ID).tenantId(TENANT).name("Caja 1")
                .apiKeyHash(passwordEncoder.encode("old-key"))
                .status(PrintAgentStatus.ACTIVE).createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createPairingCode_rotatesKeyAndReturnsUnexpiredCode() {
        PrintAgent agent = activeAgent();
        String oldHash = agent.getApiKeyHash();
        when(printAgentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent));
        when(printAgentRepository.save(any(PrintAgent.class))).thenAnswer(i -> i.getArgument(0));
        when(pairingCodeRepository.save(any(PairingCode.class))).thenAnswer(i -> i.getArgument(0));

        PairingCodeResponse res = service.createPairingCode(TENANT, AGENT_ID);

        assertThat(res.code()).hasSize(10);
        assertThat(res.expiresAt()).isAfter(LocalDateTime.now());
        assertThat(agent.getApiKeyHash()).isNotEqualTo(oldHash);
    }

    @Test
    void redeemPairingCode_validCode_returnsKeyAndStampsConsumedAndPaired() {
        PrintAgent agent = activeAgent();
        PairingCode code = PairingCode.builder()
                .code("ABCDEFGHJK").printAgentId(AGENT_ID).apiKeyPlaintext("the-key")
                .backendBaseUrl("https://api.example/v1")
                .expiresAt(LocalDateTime.now().plusMinutes(10)).createdAt(LocalDateTime.now())
                .build();
        when(pairingCodeRepository.findByCode("ABCDEFGHJK")).thenReturn(Optional.of(code));
        when(printAgentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent));
        when(pairingCodeRepository.save(any(PairingCode.class))).thenAnswer(i -> i.getArgument(0));
        when(printAgentRepository.save(any(PrintAgent.class))).thenAnswer(i -> i.getArgument(0));

        PairResponse res = service.redeemPairingCode("  abcdefghjk ");

        assertThat(res.apiKey()).isEqualTo("the-key");
        assertThat(res.backendBaseUrl()).isEqualTo("https://api.example/v1");
        assertThat(res.agentId()).isEqualTo(AGENT_ID);
        assertThat(code.getConsumedAt()).isNotNull();
        assertThat(agent.getPairedAt()).isNotNull();
    }

    @Test
    void redeemPairingCode_alreadyConsumed_throws() {
        PairingCode code = PairingCode.builder()
                .code("USEDCODE00").printAgentId(AGENT_ID).apiKeyPlaintext("k")
                .backendBaseUrl("u").expiresAt(LocalDateTime.now().plusMinutes(10))
                .consumedAt(LocalDateTime.now().minusMinutes(1)).createdAt(LocalDateTime.now())
                .build();
        when(pairingCodeRepository.findByCode("USEDCODE00")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.redeemPairingCode("USEDCODE00"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void redeemPairingCode_expired_throws() {
        PairingCode code = PairingCode.builder()
                .code("OLDCODE000").printAgentId(AGENT_ID).apiKeyPlaintext("k")
                .backendBaseUrl("u").expiresAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusMinutes(20))
                .build();
        when(pairingCodeRepository.findByCode("OLDCODE000")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.redeemPairingCode("OLDCODE000"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void saveDiscoveredPrinters_persistsListOnAgent() {
        PrintAgent agent = activeAgent();
        when(printAgentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent));
        when(printAgentRepository.save(any(PrintAgent.class))).thenAnswer(i -> i.getArgument(0));

        service.saveDiscoveredPrinters(AGENT_ID, List.of(
                new DiscoveredPrinter("EPSON L3210 Series", "EPSON L3210 Series", "USB001", true)));

        assertThat(agent.getDiscoveredPrinters()).singleElement()
                .satisfies(p -> assertThat(p.inkjetGuess()).isTrue());
    }
}
