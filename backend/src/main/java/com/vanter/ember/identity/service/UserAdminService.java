package com.vanter.ember.identity.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.dto.CreateStaffRequest;
import com.vanter.ember.identity.dto.StaffMemberResponse;
import com.vanter.ember.identity.dto.UpdateStaffProfileRequest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.restaurant.service.PlanGateService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminService {

    /** Minimum time between two admin resets of the same account. */
    static final Duration PASSWORD_RESET_COOLDOWN = Duration.ofHours(6);

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlanGateService planGateService;

    /**
     * Tenant-scoped: the target must belong to the caller's own restaurant (a bare id from
     * another tenant 404s, same as {@link #updateProfile}). CUSTOMER is rejected — that role is
     * only self-assigned via {@code POST /auth/register}. Demoting the tenant's last active ADMIN
     * is blocked for the same reason {@link #isLastActiveAdmin} guards deactivation: a restaurant
     * with zero active admins cannot be managed and cannot recover from inside the app.
     *
     * <p>Returns the {@link StaffMemberResponse} DTO, never the {@link User} entity: the entity's lazy
     * `restaurantId` proxy cannot be serialized once the persistence session is closed
     * (`open-in-view: false`), which made this endpoint answer 500 AFTER the role was already saved.
     */
    public StaffMemberResponse updateRole(String userId, UUID tenantId, Role newRole) {
        User user = requireTenantUser(userId, tenantId);
        if (newRole == Role.CUSTOMER) {
            throw new IllegalArgumentException("Cannot assign the CUSTOMER role to a staff member");
        }
        if (newRole != Role.ADMIN && isLastActiveAdmin(user, tenantId)) {
            throw new IllegalArgumentException(
                    "Cannot change the role of the last active administrator of this restaurant.");
        }
        user.setRole(newRole);
        return toStaffResponse(userRepository.save(user));
    }

    /**
     * Admin-driven staff onboarding, tenant-bound to the caller's own restaurant. Mirrors
     * {@code PlatformRestaurantService.create}'s duplicate-email guard + password-encode shape, but
     * for an existing tenant rather than a brand-new one. CUSTOMER is rejected here — that role is
     * only ever self-assigned via {@code POST /auth/register}.
     */
    public StaffMemberResponse create(UUID tenantId, CreateStaffRequest request) {
        if (request.role() == Role.CUSTOMER) {
            throw new IllegalArgumentException("Cannot create a CUSTOMER account as staff");
        }
        if (request.role() == Role.KITCHEN || request.role() == Role.ACCOUNTANT) {
            planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "roles");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }

        Restaurant restaurant = restaurantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + tenantId));

        User user = userRepository.save(User.builder()
                .restaurantId(restaurant)
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .shift(request.shift())
                .contractType(request.contractType())
                .location(request.location())
                .build());

        return toStaffResponse(user);
    }

    public List<StaffMemberResponse> getStaff(UUID tenantId) {
        return userRepository.findByRestaurantId_IdAndRoleNotOrderByNameAsc(tenantId, Role.CUSTOMER).stream()
                .map(UserAdminService::toStaffResponse)
                .toList();
    }

    public StaffMemberResponse updateProfile(
            String userId, UUID tenantId, UpdateStaffProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getRestaurantId() == null || !user.getRestaurantId().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }

        if (request.name() != null) user.setName(request.name());
        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new IllegalArgumentException("Email already in use: " + request.email());
            }
            user.setEmail(request.email());
        }
        if (request.active() != null) {
            if (Boolean.FALSE.equals(request.active())) {
                if (isLastActiveAdmin(user, tenantId)) {
                    throw new IllegalArgumentException(
                            "Cannot deactivate the last active administrator of this restaurant.");
                }
                // Deactivation already cuts off the next request (EmberUserDetailsService/
                // SecurityConfig check isEnabled()); bumping tokenVersion too means a token
                // issued right before this call can't survive a later re-activation either.
                bumpTokenVersion(user);
            }
            user.setActive(request.active());
        }
        if (request.shift() != null) user.setShift(request.shift());
        if (request.contractType() != null) user.setContractType(request.contractType());
        if (request.location() != null) user.setLocation(request.location());
        if (request.efficiencyPercentage() != null) {
            user.setEfficiencyPercentage(request.efficiencyPercentage());
        }
        if (request.pendingHours() != null) user.setPendingHours(request.pendingHours());

        return toStaffResponse(userRepository.save(user));
    }

    /**
     * Sets (or replaces) a staff member's quick-login PIN. Admin-only: no current-password check —
     * the caller is the tenant's ADMIN and cannot know the employee's password. Same tenant-scope
     * guard as {@link #updateProfile}.
     */
    public void setPin(String userId, UUID tenantId, String pin) {
        User user = requireTenantUser(userId, tenantId);
        user.setPinHash(passwordEncoder.encode(pin));
        user.setPinUpdatedAt(Instant.now());
        bumpTokenVersion(user);
        userRepository.save(user);
    }

    /**
     * Admin-driven password reset for a non-admin staff member of the caller's own tenant. The new
     * password is NOT temporary (no forced change) — the admin chooses and communicates it. Bumps
     * {@code tokenVersion} so the person's already-issued tokens stop working, records who/when, and
     * refuses a second reset of the same account within {@link #PASSWORD_RESET_COOLDOWN} (429) so the
     * option cannot be used as a constant back door. ADMIN targets stay with the platform operator.
     */
    public void resetPassword(String userId, UUID tenantId, String adminEmail, String newPassword) {
        User user = requireTenantUser(userId, tenantId);
        if (user.getRole() != Role.WAITER && user.getRole() != Role.KITCHEN
                && user.getRole() != Role.ACCOUNTANT) {
            throw new IllegalArgumentException(
                    "Only waiter, kitchen and accountant passwords can be reset by an administrator");
        }
        Instant availableAt = passwordResetAvailableAt(user);
        if (availableAt != null) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Password was reset recently; it can be reset again at " + availableAt);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordResetAt(Instant.now());
        user.setPasswordResetBy(adminEmail);
        bumpTokenVersion(user);
        userRepository.save(user);
        log.info("Password of staff member {} reset by admin {}", user.getId(), adminEmail);
    }

    /** Null when a reset is allowed now; otherwise the instant the cooldown ends. */
    private static Instant passwordResetAvailableAt(User user) {
        if (user.getPasswordResetAt() == null) {
            return null;
        }
        Instant end = user.getPasswordResetAt().plus(PASSWORD_RESET_COOLDOWN);
        return end.isAfter(Instant.now()) ? end : null;
    }

    /** Removes a staff member's quick-login PIN. Admin-only, tenant-scoped. */
    public void clearPin(String userId, UUID tenantId) {
        User user = requireTenantUser(userId, tenantId);
        user.setPinHash(null);
        user.setPinUpdatedAt(null);
        bumpTokenVersion(user);
        userRepository.save(user);
    }

    /**
     * Explicit "sign out everywhere" action (F-17): invalidates every already-issued token for
     * this user on its very next request, without waiting for it to expire. Tenant-scoped like
     * every other staff-management method here.
     */
    public void revokeSessions(String userId, UUID tenantId) {
        User user = requireTenantUser(userId, tenantId);
        bumpTokenVersion(user);
        userRepository.save(user);
    }

    private void bumpTokenVersion(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    /**
     * True when {@code target} is a currently-active ADMIN and no other active ADMIN remains in
     * the tenant. Guards both deactivation and role demotion: a restaurant with zero active
     * admins has no one who can manage staff, roles, or catalog, and (since {@code SecurityConfig}
     * only authenticates {@code isEnabled()} users) no way to undo it from inside the app — the
     * tenant is bricked. An inactive admin can't hold the fort, so it doesn't count as "another".
     */
    private boolean isLastActiveAdmin(User target, UUID tenantId) {
        if (target.getRole() != Role.ADMIN || !Boolean.TRUE.equals(target.getActive())) {
            return false;
        }
        return userRepository.findByRestaurantId_IdAndRoleAndActiveTrue(tenantId, Role.ADMIN).stream()
                .noneMatch(admin -> !admin.getId().equals(target.getId()));
    }

    private User requireTenantUser(String userId, UUID tenantId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getRestaurantId() == null || !user.getRestaurantId().getId().equals(tenantId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        return user;
    }

    private static StaffMemberResponse toStaffResponse(User user) {
        return new StaffMemberResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getActive(),
                user.getShift(),
                user.getContractType(),
                user.getLocation(),
                user.getEfficiencyPercentage(),
                user.getPendingHours(),
                user.getPinHash() != null,
                passwordResetAvailableAt(user),
                user.getPinUpdatedAt());
    }
}
