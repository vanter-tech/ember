package com.vanter.ember.platform.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.platform.model.PlatformAuditLog;
import com.vanter.ember.platform.model.PlatformOperator;
import com.vanter.ember.platform.model.dto.PlatformRestaurantAdminResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantCreateRequest;
import com.vanter.ember.platform.model.dto.PlatformRestaurantDetailResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantSummaryResponse;
import com.vanter.ember.platform.repository.PlatformAuditLogRepository;
import com.vanter.ember.platform.repository.PlatformOperatorRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.restaurant.service.RestaurantService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator-facing tenant directory (EMB-PC-06). {@link Restaurant} and {@link
 * com.vanter.ember.identity.model.User} carry no {@code @TenantId}, so these reads work with no
 * {@link com.vanter.ember.config.TenantContextHolder} binding — the {@code /platform/**} chain
 * never touches it (see {@link com.vanter.ember.platform.config.PlatformSecurityConfig}).
 */
@Service
public class PlatformRestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final RestaurantService restaurantService;
    private final PlatformOperatorRepository platformOperatorRepository;
    private final PlatformAuditLogRepository platformAuditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.vanter.ember.licensing.service.LicenseIssuingService licenseIssuingService;

    /**
     * Manual constructor (not Lombok's {@code @RequiredArgsConstructor}): {@code @Lazy} on a
     * field is never copied onto the generated constructor parameter, so it would silently NOT
     * defer {@link com.vanter.ember.licensing.service.LicenseIssuingService}'s creation — and that
     * bean throws at construction time whenever {@code hub.license.private-key} isn't set (true
     * for every non-{@code hub}-profile boot, tests included). The {@code @Lazy} has to sit on
     * this constructor's parameter to actually produce a deferred-resolution proxy here.
     */
    public PlatformRestaurantService(
            RestaurantRepository restaurantRepository,
            UserRepository userRepository,
            RestaurantService restaurantService,
            PlatformOperatorRepository platformOperatorRepository,
            PlatformAuditLogRepository platformAuditLogRepository,
            PasswordEncoder passwordEncoder,
            @Lazy com.vanter.ember.licensing.service.LicenseIssuingService licenseIssuingService) {
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
        this.restaurantService = restaurantService;
        this.platformOperatorRepository = platformOperatorRepository;
        this.platformAuditLogRepository = platformAuditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.licenseIssuingService = licenseIssuingService;
    }

    public Page<PlatformRestaurantSummaryResponse> getAll(Pageable pageable) {
        return getAll(pageable, false);
    }

    public Page<PlatformRestaurantSummaryResponse> getAll(Pageable pageable, boolean includeDeleted) {
        Page<Restaurant> page = includeDeleted
                ? restaurantRepository.findAll(pageable)
                : restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, pageable);
        return page.map(PlatformRestaurantSummaryResponse::from);
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

        if (newStatus == RestaurantStatus.DELETED) {
            throw new IllegalArgumentException(
                    "Usa DELETE /platform/restaurants/{id} para eliminar un restaurante.");
        }
        if (restaurant.getStatus() == RestaurantStatus.DELETED) {
            throw new IllegalStateException(
                    "Restaura el restaurante antes de cambiar su estado.");
        }

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

    /**
     * Soft-delete a churned tenant. Only a SUSPENDED restaurant may be deleted — the operator has
     * to suspend it first, a deliberate two-step gate. Reversible via {@link #restore}. Nothing is
     * physically removed; DELETED is just another "not ACTIVE" status, so every access gate
     * (SecurityConfig, SessionService, HubHeartbeatService) already blocks it.
     */
    @Transactional
    public void delete(UUID restaurantId, String operatorEmail) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(operatorEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
        if (restaurant.getStatus() != RestaurantStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "El restaurante debe estar suspendido antes de eliminarlo.");
        }
        String oldStatus = restaurant.getStatus().name();
        restaurant.setStatus(RestaurantStatus.DELETED);
        restaurant.setDeletedAt(Instant.now());
        restaurant.setDeletedBy(operator.getId());
        restaurantRepository.save(restaurant);

        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .operatorId(operator.getId())
                .operatorEmail(operator.getEmail())
                .restaurantId(restaurantId)
                .action("RESTAURANT_DELETED")
                .oldValue(oldStatus)
                .newValue(RestaurantStatus.DELETED.name())
                .build());
    }

    /** Reverse a {@link #delete}: DELETED -> SUSPENDED (never straight to ACTIVE). */
    @Transactional
    public PlatformRestaurantSummaryResponse restore(UUID restaurantId, String operatorEmail) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(operatorEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
        if (restaurant.getStatus() != RestaurantStatus.DELETED) {
            throw new IllegalStateException("El restaurante no está eliminado.");
        }
        restaurant.setStatus(RestaurantStatus.SUSPENDED);
        restaurant.setDeletedAt(null);
        restaurant.setDeletedBy(null);
        Restaurant saved = restaurantRepository.save(restaurant);

        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .operatorId(operator.getId())
                .operatorEmail(operator.getEmail())
                .restaurantId(restaurantId)
                .action("RESTAURANT_RESTORED")
                .oldValue(RestaurantStatus.DELETED.name())
                .newValue(RestaurantStatus.SUSPENDED.name())
                .build());

        return PlatformRestaurantSummaryResponse.from(saved);
    }

    /**
     * Operator-driven tenant onboarding: creates the {@link Restaurant} and its initial ADMIN
     * {@link User} in one request, then writes a {@link PlatformAuditLog} row — all in one
     * transaction, mirroring {@link #updateStatus}'s operator-resolve + audit-log-in-same-transaction
     * shape.
     */
    @Transactional
    public PlatformRestaurantSummaryResponse create(PlatformRestaurantCreateRequest request, String operatorEmail) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(operatorEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (restaurantRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Slug already in use: " + request.getSlug());
        }
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getAdminEmail());
        }

        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .build());

        userRepository.save(User.builder()
                .restaurantId(restaurant)
                .name(request.getAdminName())
                .email(request.getAdminEmail())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .role(Role.ADMIN)
                .build());

        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .operatorId(operator.getId())
                .operatorEmail(operator.getEmail())
                .restaurantId(restaurant.getId())
                .action("RESTAURANT_CREATED")
                .newValue(restaurant.getSlug())
                .build());

        return PlatformRestaurantSummaryResponse.from(restaurant);
    }

    /**
     * Operator-driven license issuance: signs a {@code license.key} for a restaurant that already
     * exists (created via {@link #create}), and audits it the same way as every other operator
     * action here.
     */
    @Transactional
    public String issueHubLicense(UUID restaurantId, String operatorEmail) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(operatorEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Restaurant not found: " + restaurantId);
        }

        String licenseKey = licenseIssuingService.issue(restaurantId);

        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .operatorId(operator.getId())
                .operatorEmail(operator.getEmail())
                .restaurantId(restaurantId)
                .action("HUB_LICENSE_ISSUED")
                .build());

        return licenseKey;
    }
}
