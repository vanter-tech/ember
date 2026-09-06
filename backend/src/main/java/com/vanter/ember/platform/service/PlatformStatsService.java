package com.vanter.ember.platform.service;

import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.platform.model.dto.HubStatus;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse.HubCounts;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse.TenantCounts;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read-only KPI aggregation for the operator console dashboard. */
@Service
@RequiredArgsConstructor
public class PlatformStatsService {

    private final RestaurantRepository restaurantRepository;
    private final HubActivationRepository hubActivationRepository;

    public PlatformStatsResponse get() {
        TenantCounts tenants = new TenantCounts(
                restaurantRepository.countByStatus(RestaurantStatus.ACTIVE),
                restaurantRepository.countByStatus(RestaurantStatus.SUSPENDED),
                restaurantRepository.countByStatus(RestaurantStatus.DELETED));

        Instant now = Instant.now();
        long online = 0, stale = 0, offline = 0, never = 0;
        for (HubActivation a : hubActivationRepository.findAll()) {
            switch (HubStatus.from(a.getLastHeartbeatAt(), now)) {
                case ONLINE -> online++;
                case STALE -> stale++;
                case OFFLINE -> offline++;
                case NEVER -> never++;
            }
        }
        return new PlatformStatsResponse(tenants, new HubCounts(online, stale, offline, never));
    }
}
