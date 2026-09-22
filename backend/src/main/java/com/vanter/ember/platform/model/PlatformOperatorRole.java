package com.vanter.ember.platform.model;

/**
 * A platform operator's access level for {@code /platform/**} (F-14). {@code SUPER_ADMIN} can
 * mutate tenants (create, suspend, delete, change plan/mode, issue Hub licenses); {@code SUPPORT}
 * is read-only (tenant directory, stats, audit log) — for a support/ops account that should never
 * be able to take a destructive or billing-affecting action.
 */
public enum PlatformOperatorRole {
    SUPER_ADMIN, SUPPORT
}
