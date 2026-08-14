package com.vanter.ember.config;

import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base for cross-tenant isolation regression tests over the JPA repositories.
 *
 * <p>Hibernate resolves the tenant identifier once, when the session opens, so the transaction
 * {@code @DataJpaTest} normally wraps around a test (one shared {@code EntityManager}) makes it
 * impossible to write as one tenant and read as another. {@link Propagation#NOT_SUPPORTED}
 * disables it: each repository call then runs in its own transaction and re-reads
 * {@link TenantContextHolder}, which is exactly how a real request behaves.
 *
 * <p>The trade-off is that rows are committed instead of rolled back, so every subclass must
 * purge what it wrote via {@link #deleteAll()} — invoked once per tenant after each test.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class AbstractTenantIsolationTest {

    protected static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    protected static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Runs {@code action} with {@code tenantId} bound, as {@code jwtAuthFilter} would. */
    protected static void asTenant(UUID tenantId, Runnable action) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            action.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    /** {@link #asTenant} for calls whose result is asserted on. */
    protected static <T> T readAs(UUID tenantId, Supplier<T> action) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            return action.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @AfterEach
    void purgeCommittedRows() {
        try {
            asTenant(TENANT_A, this::deleteAll);
            asTenant(TENANT_B, this::deleteAll);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Deletes every row the test committed for the currently bound tenant, children before
     * parents so foreign keys stay satisfied.
     */
    protected abstract void deleteAll();
}
