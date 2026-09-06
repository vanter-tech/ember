package com.vanter.ember.platform.model.dto;

/** Console dashboard KPIs: tenant counts by lifecycle status, Hub counts by liveness. */
public record PlatformStatsResponse(TenantCounts tenants, HubCounts hubs) {

    public record TenantCounts(long active, long suspended, long deleted) {}

    public record HubCounts(long online, long stale, long offline, long never) {}
}
