package com.vanter.ember.platform.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.platform.model.PlatformAuditLog;
import com.vanter.ember.platform.model.PlatformOperator;
import com.vanter.ember.platform.model.dto.PlatformRestaurantAdminResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantDetailResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantSummaryResponse;
import com.vanter.ember.platform.repository.PlatformAuditLogRepository;
import com.vanter.ember.platform.repository.PlatformOperatorRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.restaurant.service.RestaurantService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator-facing tenant directory (EMB-PC-06). {@link Restaurant} and {@link
 * com.vanter.ember.identity.model.User} carry no {@code @TenantId}, so these reads work with no
 * {@link com.vanter.ember.config.TenantContextHolder} binding — the {@code /platform/**} chain
 * never touches it (see {@link com.vanter.ember.platform.config.PlatformSecurityConfig}).
 */
@Service
@RequiredArgsConstructor
public class PlatformRestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final RestaurantService restaurantService;
    private final PlatformOperatorRepository platformOperatorRepository;
    private final PlatformAuditLogRepository platformAuditLogRepository;

    public Page<PlatformRestaurantSummaryResponse> getAll(Pageable pageable) {
        return restaurantRepository.findAll(pageable).map(PlatformRestaurantSummaryResponse::from);
    }

    public PlatformRestaurantDetailResponse getById(UUID id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + id));

        var admins = userRepository.findByRestaurantId_IdAndRole(id, Role.ADMIN).stream()
                .map(PlatformRestaurantAdminResponse::from)
                .toList();

        return PlatformRestaurantDetailResponse.from(restaurant, admins);
    }

    /**
     * Wires the previously-unreachable {@link RestaurantService#updateStatus}: only a platform
     * operator (never the tenant's own ADMIN) may suspend/reactivate a tenant. Writes a
     * {@link PlatformAuditLog} row in the same transaction as the status change.
     */
    @Transactional
    public PlatformRestaurantSummaryResponse updateStatus(UUID restaurantId, RestaurantStatus newStatus,
                                                            String operatorEmail) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(operatorEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
        RestaurantStatus oldStatus = restaurant.getStatus();

        Restaurant updated = restaurantService.updateStatus(restaurantId, newStatus);

        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .operatorId(operator.getId())
                .operatorEmail(operator.getEmail())
                .restaurantId(restaurantId)
                .action("RESTAURANT_STATUS_UPDATED")
                .oldValue(oldStatus.name())
                .newValue(newStatus.name())
                .build());

        return PlatformRestaurantSummaryResponse.from(updated);
    }
}
