package com.vanter.ember.platform.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.platform.model.dto.PlatformRestaurantAdminResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantDetailResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantSummaryResponse;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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
}
