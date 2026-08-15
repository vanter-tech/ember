package com.vanter.ember.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.platform.model.PlatformOperator;
import com.vanter.ember.platform.model.dto.PlatformRestaurantDetailResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantSummaryResponse;
import com.vanter.ember.platform.repository.PlatformAuditLogRepository;
import com.vanter.ember.platform.repository.PlatformOperatorRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.restaurant.service.RestaurantService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(MockitoExtension.class)
class PlatformRestaurantServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @Mock UserRepository userRepository;
    @Mock RestaurantService restaurantService;
    @Mock PlatformOperatorRepository platformOperatorRepository;
    @Mock PlatformAuditLogRepository platformAuditLogRepository;
    @InjectMocks PlatformRestaurantService platformRestaurantService;

    private Restaurant restaurant() {
        return Restaurant.builder()
                .id(UUID.randomUUID())
                .name("Tenant Grill")
                .slug("tenant-grill")
                .plan(RestaurantPlan.PRO)
                .status(RestaurantStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void getAll_mapsRestaurantsToSummaries() {
        Restaurant restaurant = restaurant();
        Pageable pageable = PageRequest.of(0, 20);
        when(restaurantRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(restaurant)));

        var result = platformRestaurantService.getAll(pageable);

        assertThat(result.getContent()).hasSize(1);
        PlatformRestaurantSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.getId()).isEqualTo(restaurant.getId());
        assertThat(summary.getSlug()).isEqualTo("tenant-grill");
        assertThat(summary.getPlan()).isEqualTo(RestaurantPlan.PRO);
    }

    @Test
    void getById_includesAdminUsers() {
        Restaurant restaurant = restaurant();
        User admin = User.builder()
                .id("u-1")
                .name("Owner Admin")
                .email("owner@tenant-grill.local")
                .role(Role.ADMIN)
                .build();
        when(restaurantRepository.findById(restaurant.getId())).thenReturn(Optional.of(restaurant));
        when(userRepository.findByRestaurantId_IdAndRole(restaurant.getId(), Role.ADMIN))
                .thenReturn(List.of(admin));

        PlatformRestaurantDetailResponse result = platformRestaurantService.getById(restaurant.getId());

        assertThat(result.getId()).isEqualTo(restaurant.getId());
        assertThat(result.getAdmins()).hasSize(1);
        assertThat(result.getAdmins().get(0).getEmail()).isEqualTo("owner@tenant-grill.local");
    }

    @Test
    void getById_throwsWhenRestaurantNotFound() {
        UUID id = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformRestaurantService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_updatesRestaurantAndWritesAuditLog() {
        Restaurant restaurant = restaurant();
        UUID restaurantId = restaurant.getId();
        PlatformOperator operator = PlatformOperator.builder()
                .id(UUID.randomUUID())
                .name("Platform Admin")
                .email("operator@ember.local")
                .build();
        Restaurant updated = restaurant();
        updated.setStatus(RestaurantStatus.SUSPENDED);

        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantService.updateStatus(restaurantId, RestaurantStatus.SUSPENDED)).thenReturn(updated);

        PlatformRestaurantSummaryResponse result = platformRestaurantService.updateStatus(
                restaurantId, RestaurantStatus.SUSPENDED, "operator@ember.local");

        assertThat(result.getStatus()).isEqualTo(RestaurantStatus.SUSPENDED);

        ArgumentCaptor<com.vanter.ember.platform.model.PlatformAuditLog> captor =
                ArgumentCaptor.forClass(com.vanter.ember.platform.model.PlatformAuditLog.class);
        org.mockito.Mockito.verify(platformAuditLogRepository).save(captor.capture());
        com.vanter.ember.platform.model.PlatformAuditLog logged = captor.getValue();
        assertThat(logged.getOperatorId()).isEqualTo(operator.getId());
        assertThat(logged.getOperatorEmail()).isEqualTo("operator@ember.local");
        assertThat(logged.getRestaurantId()).isEqualTo(restaurantId);
        assertThat(logged.getAction()).isEqualTo("RESTAURANT_STATUS_UPDATED");
        assertThat(logged.getOldValue()).isEqualTo("ACTIVE");
        assertThat(logged.getNewValue()).isEqualTo("SUSPENDED");
    }

    @Test
    void updateStatus_throwsWhenRestaurantNotFound() {
        UUID restaurantId = UUID.randomUUID();
        PlatformOperator operator = PlatformOperator.builder()
                .id(UUID.randomUUID())
                .email("operator@ember.local")
                .build();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformRestaurantService.updateStatus(
                restaurantId, RestaurantStatus.SUSPENDED, "operator@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_throwsWhenOperatorNotFound() {
        UUID restaurantId = UUID.randomUUID();
        when(platformOperatorRepository.findByEmail("ghost@ember.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformRestaurantService.updateStatus(
                restaurantId, RestaurantStatus.SUSPENDED, "ghost@ember.local"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
