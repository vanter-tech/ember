package com.vanter.ember.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.dto.CreateStaffRequest;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.dto.UpdateStaffProfileRequest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock UserRepository userRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock PasswordEncoder passwordEncoder;
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

    private Restaurant restaurantFor(UUID tenantId) {
        return Restaurant.builder().id(tenantId).name("Test").slug("test-" + tenantId).build();
    }

    private User adminFor(UUID tenantId, String id) {
        return User.builder()
                .id(id)
                .name("Admin " + id)
                .email(id + "@test.com")
                .role(Role.ADMIN)
                .active(true)
                .passwordHash("hash")
                .restaurantId(Restaurant.builder().id(tenantId).name("Test").slug("test-" + tenantId).build())
                .build();
    }

    @Test
    void create_savesEncodedPasswordAndTenantBoundUser() {
        Restaurant restaurant = restaurantFor(TENANT_A);
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(restaurantRepository.findById(TENANT_A)).thenReturn(Optional.of(restaurant));
        when(passwordEncoder.encode("Sup3r$ecret")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u-new");
            return u;
        });

        var result = userAdminService.create(
                TENANT_A, new CreateStaffRequest(
                        "Ana", "ana@test.com", "Sup3r$ecret", Role.WAITER,
                        "Mesera", "Mañana", "Tiempo completo", "Sucursal Centro"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(captor.getValue().getRestaurantId()).isEqualTo(restaurant);
        assertThat(captor.getValue().getRole()).isEqualTo(Role.WAITER);
        assertThat(result.id()).isEqualTo("u-new");
        assertThat(result.name()).isEqualTo("Ana");
        assertThat(result.role()).isEqualTo(Role.WAITER);
    }

    @Test
    void create_throwsWhenRoleIsCustomer() {
        assertThatThrownBy(() -> userAdminService.create(
                TENANT_A, new CreateStaffRequest(
                        "Ana", "ana@test.com", "Sup3r$ecret", Role.CUSTOMER,
                        "Mesera", "Mañana", "Tiempo completo", "Sucursal Centro")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CUSTOMER");
    }

    @Test
    void create_throwsWhenEmailAlreadyInUse() {
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userAdminService.create(
                TENANT_A, new CreateStaffRequest(
                        "Ana", "ana@test.com", "Sup3r$ecret", Role.WAITER,
                        "Mesera", "Mañana", "Tiempo completo", "Sucursal Centro")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void create_throwsWhenRestaurantNotFound() {
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(restaurantRepository.findById(TENANT_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminService.create(
                TENANT_A, new CreateStaffRequest(
                        "Ana", "ana@test.com", "Sup3r$ecret", Role.WAITER,
                        "Mesera", "Mañana", "Tiempo completo", "Sucursal Centro")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStaff_mapsUserFieldsIntoResponse() {
        when(userRepository.findByRestaurantId_IdAndRoleNotOrderByNameAsc(TENANT_A, Role.CUSTOMER))
                .thenReturn(List.of(waiterFor(TENANT_A)));

        List<com.vanter.ember.identity.dto.StaffMemberResponse> result = userAdminService.getStaff(TENANT_A);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("u-1");
        assertThat(result.get(0).name()).isEqualTo("Ana");
        assertThat(result.get(0).email()).isEqualTo("ana@test.com");
        assertThat(result.get(0).role()).isEqualTo(Role.WAITER);
        assertThat(result.get(0).active()).isTrue();
    }

    @Test
    void updateRole_appliesRoleOnHappyPath() {
        User existing = waiterFor(TENANT_A);
        when(userRepository.findById("u-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = userAdminService.updateRole("u-1", TENANT_A, Role.KITCHEN);

        assertThat(result.getRole()).isEqualTo(Role.KITCHEN);
    }

    @Test
    void updateRole_throwsWhenTargetBelongsToAnotherTenant() {
        when(userRepository.findById("u-1")).thenReturn(Optional.of(waiterFor(UUID.randomUUID())));

        assertThatThrownBy(() -> userAdminService.updateRole("u-1", TENANT_A, Role.KITCHEN))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateRole_rejectsAssigningTheCustomerRole() {
        when(userRepository.findById("u-1")).thenReturn(Optional.of(waiterFor(TENANT_A)));

        assertThatThrownBy(() -> userAdminService.updateRole("u-1", TENANT_A, Role.CUSTOMER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CUSTOMER");
    }

    @Test
    void updateRole_blocksDemotingTheSoleActiveAdmin() {
        User admin = adminFor(TENANT_A, "a-1");
        when(userRepository.findById("a-1")).thenReturn(Optional.of(admin));
        when(userRepository.findByRestaurantId_IdAndRoleAndActiveTrue(TENANT_A, Role.ADMIN))
                .thenReturn(List.of(admin));

        assertThatThrownBy(() -> userAdminService.updateRole("a-1", TENANT_A, Role.WAITER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last active administrator");
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateRole_allowsDemotingAnAdminWhenAnotherActiveAdminRemains() {
        User admin = adminFor(TENANT_A, "a-1");
        when(userRepository.findById("a-1")).thenReturn(Optional.of(admin));
        when(userRepository.findByRestaurantId_IdAndRoleAndActiveTrue(TENANT_A, Role.ADMIN))
                .thenReturn(List.of(admin, adminFor(TENANT_A, "a-2")));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = userAdminService.updateRole("a-1", TENANT_A, Role.WAITER);

        assertThat(result.getRole()).isEqualTo(Role.WAITER);
    }

    @Test
    void updateProfile_appliesOnlyNonNullFields() {
        User existing = waiterFor(TENANT_A);
        existing.setShift("Mañana");
        when(userRepository.findById("u-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = userAdminService.updateProfile(
                "u-1", TENANT_A,
                new UpdateStaffProfileRequest(false, null, null, null, null, null, null, null, null));

        assertThat(result.active()).isFalse();
        assertThat(result.shift()).isEqualTo("Mañana");
    }

    @Test
    void updateProfile_withAllNullRequest_changesNothing() {
        User existing = waiterFor(TENANT_A);
        existing.setShift("Mañana");
        when(userRepository.findById("u-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = userAdminService.updateProfile(
                "u-1", TENANT_A,
                new UpdateStaffProfileRequest(null, null, null, null, null, null, null, null, null));

        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(result.name()).isEqualTo(existing.getName());
        assertThat(result.email()).isEqualTo(existing.getEmail());
        assertThat(result.role()).isEqualTo(existing.getRole());
        assertThat(result.active()).isEqualTo(existing.getActive());
        assertThat(result.jobTitle()).isEqualTo(existing.getJobTitle());
        assertThat(result.shift()).isEqualTo(existing.getShift());
        assertThat(result.contractType()).isEqualTo(existing.getContractType());
        assertThat(result.location()).isEqualTo(existing.getLocation());
        assertThat(result.efficiencyPercentage()).isEqualTo(existing.getEfficiencyPercentage());
        assertThat(result.pendingHours()).isEqualTo(existing.getPendingHours());
    }

    @Test
    void updateProfile_appliesNameAndEmail() {
        User existing = waiterFor(TENANT_A);
        when(userRepository.findById("u-1")).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("ana.new@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = userAdminService.updateProfile("u-1", TENANT_A, new UpdateStaffProfileRequest(
                null, null, null, null, null, null, null, "Ana Nueva", "ana.new@test.com"));

        assertThat(result.name()).isEqualTo("Ana Nueva");
        assertThat(result.email()).isEqualTo("ana.new@test.com");
    }

    @Test
    void updateProfile_settingEmailToItsOwnCurrentValueDoesNotThrow() {
        User existing = waiterFor(TENANT_A);
        when(userRepository.findById("u-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = userAdminService.updateProfile("u-1", TENANT_A, new UpdateStaffProfileRequest(
                null, null, null, null, null, null, null, null, "ana@test.com"));

        assertThat(result.email()).isEqualTo("ana@test.com");
    }

    @Test
    void updateProfile_throwsWhenNewEmailAlreadyInUseByAnotherUser() {
        User existing = waiterFor(TENANT_A);
        when(userRepository.findById("u-1")).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userAdminService.updateProfile("u-1", TENANT_A, new UpdateStaffProfileRequest(
                null, null, null, null, null, null, null, null, "taken@test.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void updateProfile_throwsWhenUserBelongsToAnotherTenant() {
        when(userRepository.findById("u-1")).thenReturn(Optional.of(waiterFor(UUID.randomUUID())));

        assertThatThrownBy(() -> userAdminService.updateProfile(
                "u-1", TENANT_A,
                new UpdateStaffProfileRequest(false, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_throwsWhenUserDoesNotExist() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAdminService.updateProfile(
                "missing", TENANT_A,
                new UpdateStaffProfileRequest(false, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_blocksDeactivatingTheSoleActiveAdmin() {
        User admin = adminFor(TENANT_A, "a-1");
        when(userRepository.findById("a-1")).thenReturn(Optional.of(admin));
        when(userRepository.findByRestaurantId_IdAndRoleAndActiveTrue(TENANT_A, Role.ADMIN))
                .thenReturn(List.of(admin));

        assertThatThrownBy(() -> userAdminService.updateProfile(
                "a-1", TENANT_A,
                new UpdateStaffProfileRequest(false, null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last active administrator");

        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateProfile_allowsDeactivatingAnAdminWhenAnotherActiveAdminRemains() {
        User admin = adminFor(TENANT_A, "a-1");
        when(userRepository.findById("a-1")).thenReturn(Optional.of(admin));
        when(userRepository.findByRestaurantId_IdAndRoleAndActiveTrue(TENANT_A, Role.ADMIN))
                .thenReturn(List.of(admin, adminFor(TENANT_A, "a-2")));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = userAdminService.updateProfile(
                "a-1", TENANT_A,
                new UpdateStaffProfileRequest(false, null, null, null, null, null, null, null, null));

        assertThat(result.active()).isFalse();
    }

    @Test
    void setPin_storesBcryptHashAndTimestamp_withoutAnyPasswordCheck() {
        User existing = waiterFor(TENANT_A);
        when(userRepository.findById("u-1")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("1234")).thenReturn("pinHash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userAdminService.setPin("u-1", TENANT_A, "1234");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPinHash()).isEqualTo("pinHash");
        assertThat(captor.getValue().getPinUpdatedAt()).isNotNull();
    }

    @Test
    void setPin_throwsWhenUserBelongsToAnotherTenant() {
        when(userRepository.findById("u-1")).thenReturn(Optional.of(waiterFor(UUID.randomUUID())));

        assertThatThrownBy(() -> userAdminService.setPin("u-1", TENANT_A, "1234"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clearPin_nullsBothColumns() {
        User existing = waiterFor(TENANT_A);
        existing.setPinHash("pinHash");
        existing.setPinUpdatedAt(java.time.Instant.now());
        when(userRepository.findById("u-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userAdminService.clearPin("u-1", TENANT_A);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPinHash()).isNull();
        assertThat(captor.getValue().getPinUpdatedAt()).isNull();
    }

    @Test
    void getStaff_reportsHasPinWhenPinHashPresent() {
        User withPin = waiterFor(TENANT_A);
        withPin.setPinHash("pinHash");
        when(userRepository.findByRestaurantId_IdAndRoleNotOrderByNameAsc(TENANT_A, Role.CUSTOMER))
                .thenReturn(List.of(withPin));

        assertThat(userAdminService.getStaff(TENANT_A).get(0).hasPin()).isTrue();
    }
}
