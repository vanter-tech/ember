package com.vanter.ember.printing.controller;

import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.dto.AgentTokenRequest;
import com.vanter.ember.printing.dto.AgentTokenResponse;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.service.PrintAgentService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent-facing, unauthenticated (permitAll in SecurityConfig) — the API key IS the
 * credential. Never protected by the tenant JWT filter, same class of exception as
 * {@code /public/restaurants/{slug}/branding}.
 */
@RestController
@RequestMapping("/printing/agents")
@RequiredArgsConstructor
public class PrintAgentAuthController {

    private static final long AGENT_TOKEN_TTL_MS = 20 * 60 * 1000L;

    private final PrintAgentService printAgentService;
    private final JwtService jwtService;

    @PostMapping("/token")
    public AgentTokenResponse token(@Valid @RequestBody AgentTokenRequest request) {
        PrintAgent agent = printAgentService.authenticateByApiKey(request.apiKey());
        printAgentService.markSeen(agent);
        String token = jwtService.generateToken(
                agent.getId().toString(),
                Map.of("rid", agent.getTenantId().toString(), "typ", "print-agent"),
                AGENT_TOKEN_TTL_MS);
        return new AgentTokenResponse(token, AGENT_TOKEN_TTL_MS / 1000);
    }
}
