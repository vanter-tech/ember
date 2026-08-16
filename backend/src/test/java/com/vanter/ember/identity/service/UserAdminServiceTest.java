package com.vanter.ember.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.dto.UpdateStaffProfileRequest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserAdminService userAdminService;

    private static final UUID TENANT_A = UUID.randomUUID();

    private User waiterFor(UUID tenantId) {
        return User.builder()
                .id("u-1")
                .name("Ana")
                .email("ana@test.com")
                .role(Role.WAITER)
                .passwordHash("hash")
                .restaurantId(Restaurant.builder().id(tenantId).name("Test").slug("test-" + tenantId).build())
                .build();
    }

    @Test
    void getStaff_mapsUserFieldsIntoResponse() {
        when(userRepository.findByRestaurantId_IdAndRoleNot(TENANT_A, Role.CUSTOMER))
                .thenReturn(List.of(waiterFor(TENANT_A)));

        List<com.vanter.ember.identity.dto.StaffMemberResponse> result = userAdminService.getStaff(TENANT_A);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("u-1");
        assertThat(result.get(0).role()).isEqualTo(Role.WAITER);
        assertThat(result.get(0).active()).isTrue();
    }

    @Test
    void updateProfile_appliesOnlyNonNullFields() {
        User existing = waiterFor(TENANT_A);
        existing.setShift("Mañana");
        when(userRepository.findById("u-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = userAdminService.updateProfile(
                "u-1", TENANT_A, new UpdateStaffProfileRequest(false, null, null, null, null, null, null));

        assertThat(result.active()).isFalse();
        assertThat(result.shift()).isEqualTo("Mañana");
    }

    @Test
    void updateProfile_throwsWhenUserBelongsToAnotherTenant() {
        when(userRepository.findById("u-1")).thenReturn(Optional.of(waiterFor(UUID.randomUUID())));

        assertThatThrownBy(() -> userAdminService.updateProfile(
                "u-1", TENANT_A, new UpdateStaffProfileRequest(false, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_throwsWhenUserDoesNotExist() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminService.updateProfile(
                "missing", TENANT_A, new UpdateStaffProfileRequest(false, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
