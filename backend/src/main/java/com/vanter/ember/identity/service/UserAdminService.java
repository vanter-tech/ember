package com.vanter.ember.identity.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.dto.CreateStaffRequest;
import com.vanter.ember.identity.dto.StaffMemberResponse;
import com.vanter.ember.identity.dto.UpdateStaffProfileRequest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;

    public User updateRole(String userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setRole(newRole);
        return userRepository.save(user);
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
                .jobTitle(request.jobTitle())
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
                assertNotLastActiveAdmin(user, tenantId);
            }
            user.setActive(request.active());
        }
        if (request.jobTitle() != null) user.setJobTitle(request.jobTitle());
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
        userRepository.save(user);
    }

    /** Removes a staff member's quick-login PIN. Admin-only, tenant-scoped. */
    public void clearPin(String userId, UUID tenantId) {
        User user = requireTenantUser(userId, tenantId);
        user.setPinHash(null);
        user.setPinUpdatedAt(null);
        userRepository.save(user);
    }

    /**
     * Blocks deactivating the tenant's last active ADMIN — including an admin deactivating their
     * own account. A restaurant with zero active admins has no one who can manage staff, roles,
     * or catalog, and (since {@code SecurityConfig} only authenticates {@code isEnabled()} users)
     * no way to undo it from inside the app: the tenant is bricked. Deactivating an admin while
     * another active admin remains is allowed and reversible.
     */
    private void assertNotLastActiveAdmin(User target, UUID tenantId) {
        if (target.getRole() != Role.ADMIN || !Boolean.TRUE.equals(target.getActive())) {
            return;
        }
        List<User> activeAdmins =
                userRepository.findByRestaurantId_IdAndRoleAndActiveTrue(tenantId, Role.ADMIN);
        boolean anotherActiveAdminExists = activeAdmins.stream()
                .anyMatch(admin -> !admin.getId().equals(target.getId()));
        if (!anotherActiveAdminExists) {
            throw new IllegalArgumentException(
                    "Cannot deactivate the last active administrator of this restaurant.");
        }
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
                user.getJobTitle(),
                user.getShift(),
                user.getContractType(),
                user.getLocation(),
                user.getEfficiencyPercentage(),
                user.getPendingHours(),
                user.getPinHash() != null);
    }
}
