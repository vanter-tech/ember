package com.vanter.ember.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextHolderTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void setAndGet_returnsBoundTenant() {
        UUID tenantId = UUID.randomUUID();

        TenantContextHolder.setTenantId(tenantId);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(tenantId);
        assertThat(TenantContextHolder.requireTenantId()).isEqualTo(tenantId);
    }

    @Test
    void clear_removesBoundTenant() {
        TenantContextHolder.setTenantId(UUID.randomUUID());

        TenantContextHolder.clear();

        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void requireTenantId_withoutTenant_throws() {
        assertThatThrownBy(TenantContextHolder::requireTenantId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant bound");
    }

    @Test
    void tenant_isNotVisibleFromAnotherThread() {
        TenantContextHolder.setTenantId(UUID.randomUUID());

        UUID seenByOtherThread = CompletableFuture
                .supplyAsync(TenantContextHolder::getTenantId)
                .join();

        assertThat(seenByOtherThread).isNull();
    }
}
