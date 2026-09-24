package com.vanter.ember.printing.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.logo.TicketLogoService;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import com.vanter.ember.settings.service.SettingService;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent-facing (own JWT, {@code permitAll} in SecurityConfig like the rest of {@code /agents/me}):
 * serves the restaurant's receipt logo already dithered to 1-bit for the configured paper width.
 * The tenant comes strictly from the signed agent JWT's agent record, never from the request.
 * Supports {@code ETag}/{@code If-None-Match} so the agent only re-downloads when it changed.
 */
@RestController
@RequestMapping("/printing/agents/me/ticket-logo")
@RequiredArgsConstructor
public class PrintAgentLogoController {

    private final JwtService jwtService;
    private final PrintAgentRepository printAgentRepository;
    private final TicketLogoService ticketLogoService;
    private final SettingService settingService;

    @GetMapping
    public ResponseEntity<byte[]> logo(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        UUID agentId = UUID.fromString(jwtService.extractSubject(authHeader.substring("Bearer ".length())));
        Optional<PrintAgent> agent = printAgentRepository.findById(agentId);
        if (agent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        UUID tenantId = agent.get().getTenantId();

        // This route has no tenant bound (permitAll), but settings are @TenantId-scoped.
        UUID previous = TenantContextHolder.getTenantId();
        TenantContextHolder.setTenantId(tenantId);
        Optional<byte[]> png;
        try {
            var paperWidth = settingService.getSettings(tenantId).getPayload().getTicket().getPaperWidth();
            png = ticketLogoService.loadBitonal(tenantId, paperWidth);
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.setTenantId(previous);
            }
        }
        if (png.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String etag = "\"" + sha256(png.get()) + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).contentType(MediaType.IMAGE_PNG).body(png.get());
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
