package com.vanter.ember.config;

import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Feeds Hibernate's DISCRIMINATOR multi-tenancy from {@link TenantContextHolder}, so every
 * {@code @TenantId} entity is filtered on read and stamped on write without callers passing
 * a restaurant id by hand.
 *
 * <p>Hibernate rejects a null tenant identifier, so unbound contexts (login/registration,
 * repository tests, startup schema work) resolve to {@link #NO_TENANT} — a sentinel that owns
 * no real rows and therefore reads and writes an empty partition rather than leaking across
 * tenants.
 */
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    /** Sentinel used when no tenant is bound to the current thread. */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenantId = TenantContextHolder.getTenantId();
        return tenantId != null ? tenantId : NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
