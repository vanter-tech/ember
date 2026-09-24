package com.vanter.ember.printing.logo;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.service.PlanGateService;
import com.vanter.ember.settings.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Ticket logo", description = "Receipt logo printed at the top of the bill ticket")
@RestController
@RequestMapping("/settings/ticket-logo")
@RequiredArgsConstructor
public class TicketLogoController {

    private final TicketLogoService ticketLogoService;
    private final SettingService settingService;
    private final PlanGateService planGateService;

    @Operation(summary = "Upload or replace the receipt logo (ADMIN)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void upload(@RequestParam("file") MultipartFile file) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "branding");
        ticketLogoService.store(tenantId, file);
    }

    @Operation(summary = "Remove the receipt logo (ADMIN)")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void remove() {
        ticketLogoService.delete(TenantContextHolder.requireTenantId());
    }

    @Operation(summary = "Preview of the logo exactly as it prints (1-bit, sized to the paper width) (ADMIN)")
    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> preview() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        var paperWidth = settingService.getSettings(tenantId).getPayload().getTicket().getPaperWidth();
        byte[] png = ticketLogoService
                .loadBitonal(tenantId, paperWidth)
                .orElseThrow(() -> new ResourceNotFoundException("No ticket logo configured"));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }
}
