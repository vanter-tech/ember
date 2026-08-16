package com.vanter.ember.config;

import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.platform.service.PlatformJwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the {@code /platform/**} chain (EMB-PC-04) and the tenant chain reject each other's
 * tokens outright: the two are signed with disjoint secrets ({@link JwtService} vs
 * {@link PlatformJwtService}), so a token from one fails signature verification under the other
 * and never reaches either {@code UserDetailsService}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlatformAuthIsolationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired PlatformJwtService platformJwtService;

    @Test
    void tenantToken_rejectedOnPlatformRoute() throws Exception {
        String tenantToken = jwtService.generateToken(
                "admin@tenant.local", Map.of("rid", UUID.randomUUID().toString(), "role", "ADMIN"));

        mockMvc.perform(get("/platform/restaurants")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformToken_rejectedOnTenantRoute() throws Exception {
        String platformToken = platformJwtService.generateToken(
                "operator@ember.local", Map.of("role", "PLATFORM_ADMIN"));

        mockMvc.perform(get("/catalog/categories")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isUnauthorized());
    }
}
