package com.vanter.ember.export.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.export.service.ExportService;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExportController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class ExportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ExportService exportService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void export_returnsTheWorkbookWithAttachmentHeaders() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        byte[] fixtureWorkbook = {80, 75, 3, 4};
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 14, 23, 59, 59);
        when(exportService.buildTenantExportWorkbook(TENANT_ID, from, to)).thenReturn(fixtureWorkbook);

        mockMvc.perform(get("/admin/export")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-08-14T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes(fixtureWorkbook));

        verify(exportService).buildTenantExportWorkbook(TENANT_ID, from, to);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void export_withoutParams_passesNullBoundsToTheService() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(exportService.buildTenantExportWorkbook(TENANT_ID, null, null)).thenReturn(new byte[0]);

        mockMvc.perform(get("/admin/export")).andExpect(status().isOk());

        verify(exportService).buildTenantExportWorkbook(TENANT_ID, null, null);
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void export_forbiddenForNonAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/admin/export")).andExpect(status().isForbidden());

        verify(exportService, never()).buildTenantExportWorkbook(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void export_withoutTenantBound_isRejected() throws Exception {
        mockMvc.perform(get("/admin/export")).andExpect(status().isConflict());

        verify(exportService, never()).buildTenantExportWorkbook(any(), any(), any());
    }

    @Test
    void export_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/admin/export")).andExpect(status().isUnauthorized());

        verify(exportService, never()).buildTenantExportWorkbook(any(), any(), any());
    }
}
