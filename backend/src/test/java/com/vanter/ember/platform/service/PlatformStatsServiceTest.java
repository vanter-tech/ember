package com.vanter.ember.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformStatsServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @Mock HubActivationRepository hubActivationRepository;
    @InjectMocks PlatformStatsService platformStatsService;

    private HubActivation hub(Instant lastBeat) {
        return HubActivation.builder()
                .restaurantId(UUID.randomUUID()).hardwareFingerprint("fp")
                .activatedAt(Instant.now()).lastHeartbeatAt(lastBeat).build();
    }

    @Test
    void get_countsTenantsByStatusAndHubsByLiveness() {
        when(restaurantRepository.countByStatus(RestaurantStatus.ACTIVE)).thenReturn(5L);
        when(restaurantRepository.countByStatus(RestaurantStatus.SUSPENDED)).thenReturn(2L);
        when(restaurantRepository.countByStatus(RestaurantStatus.DELETED)).thenReturn(1L);
        Instant now = Instant.now();
        when(hubActivationRepository.findAll()).thenReturn(List.of(
                hub(now.minus(Duration.ofMinutes(2))),   // online
                hub(now.minus(Duration.ofMinutes(2))),   // online
                hub(now.minus(Duration.ofHours(3))),     // stale
                hub(now.minus(Duration.ofDays(3))),      // offline
                hub(null)));                              // never

        PlatformStatsResponse stats = platformStatsService.get();

        assertThat(stats.tenants().active()).isEqualTo(5);
        assertThat(stats.tenants().suspended()).isEqualTo(2);
        assertThat(stats.tenants().deleted()).isEqualTo(1);
        assertThat(stats.hubs().online()).isEqualTo(2);
        assertThat(stats.hubs().stale()).isEqualTo(1);
        assertThat(stats.hubs().offline()).isEqualTo(1);
        assertThat(stats.hubs().never()).isEqualTo(1);
    }

    @Test
    void get_allZeroesWhenEmpty() {
        when(restaurantRepository.countByStatus(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(hubActivationRepository.findAll()).thenReturn(List.of());

        PlatformStatsResponse stats = platformStatsService.get();

        assertThat(stats.hubs().online()).isZero();
        assertThat(stats.hubs().never()).isZero();
    }
}
