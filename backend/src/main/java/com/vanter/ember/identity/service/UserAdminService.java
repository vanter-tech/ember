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

        if (request.active() != null) user.setActive(request.active());
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
                user.getPendingHours());
    }
}
