/**
 * Read-only business analytics for the ADMIN panel, served under {@code /admin/analytics}.
 *
 * <p>The module owns no persistence of its own: it aggregates over the {@code billing},
 * {@code session} and {@code catalog} models and is tenant-scoped through
 * {@link com.vanter.ember.config.TenantContextHolder} like every other tenant-facing module.
 */
package com.vanter.ember.analytics;
