package com.vanter.ember.printing.controller;

import com.vanter.ember.printing.dto.PairRequest;
import com.vanter.ember.printing.dto.PairResponse;
import com.vanter.ember.printing.service.PairAttemptGuard;
import com.vanter.ember.printing.service.PrintAgentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent-facing, unauthenticated (permitAll in SecurityConfig) — the pairing code IS the
 * credential, redeemed once for the API key. Same trust boundary as {@code /printing/agents/token}.
 */
@RestController
@RequestMapping("/printing/agents")
@RequiredArgsConstructor
public class PrintAgentPairingController {

    private final PrintAgentService printAgentService;
    private final PairAttemptGuard pairAttemptGuard;

    @PostMapping("/pair")
    public PairResponse pair(@Valid @RequestBody PairRequest request, HttpServletRequest http) {
        String ip = clientIp(http);
        pairAttemptGuard.assertNotLocked(ip);
        try {
            PairResponse response = printAgentService.redeemPairingCode(request.code());
            pairAttemptGuard.recordSuccess(ip);
            return response;
        } catch (RuntimeException e) {
            pairAttemptGuard.recordFailure(ip);
            throw e;
        }
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
