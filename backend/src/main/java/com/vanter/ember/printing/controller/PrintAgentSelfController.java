package com.vanter.ember.printing.controller;

import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.dto.PrinterConfigResponse;
import com.vanter.ember.printing.dto.ReportDiscoveredPrintersRequest;
import com.vanter.ember.printing.model.PrinterConfig;
import com.vanter.ember.printing.repository.PrinterConfigRepository;
import com.vanter.ember.printing.service.PrintAgentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent-facing (own JWT, not a tenant user session) — "what printers am I responsible for".
 * Safe to {@code permitAll} (see SecurityConfig): {@code agentId} is derived strictly from a
 * valid, signed agent JWT parsed here, same trust boundary as the token endpoint itself.
 */
@RestController
@RequestMapping("/printing/agents/me")
@RequiredArgsConstructor
public class PrintAgentSelfController {

    private final PrinterConfigRepository printerConfigRepository;
    private final JwtService jwtService;
    private final PrintAgentService printAgentService;

    @GetMapping("/printers")
    public List<PrinterConfigResponse> myPrinters(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring("Bearer ".length());
        UUID agentId = UUID.fromString(jwtService.extractSubject(token));
        return printerConfigRepository.findByAgentIdAndActiveTrue(agentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/discovered-printers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportDiscoveredPrinters(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ReportDiscoveredPrintersRequest request) {
        String token = authHeader.substring("Bearer ".length());
        UUID agentId = UUID.fromString(jwtService.extractSubject(token));
        printAgentService.saveDiscoveredPrinters(agentId, request.printers());
    }

    private PrinterConfigResponse toResponse(PrinterConfig config) {
        return new PrinterConfigResponse(
                config.getId(), config.getAgentId(), config.getRole().name(),
                config.getConnectionType().name(), config.getHost(), config.getPort(),
                config.getComPort(), config.getWindowsQueueName(), config.getRenderMode().name(),
                config.getLabel(), config.isActive());
    }
}
