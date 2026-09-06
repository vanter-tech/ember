package com.vanter.ember.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.platform.model.PlatformOperator;
import com.vanter.ember.platform.model.dto.PlatformRestaurantCreateRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PlatformRestaurantServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @Mock UserRepository userRepository;
    @Mock RestaurantService restaurantService;
    @Mock PlatformOperatorRepository platformOperatorRepository;
    @Mock PlatformAuditLogRepository platformAuditLogRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock com.vanter.ember.licensing.service.LicenseIssuingService licenseIssuingService;
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
        when(restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, pageable))
                .thenReturn(new PageImpl<>(List.of(restaurant)));

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

    private com.vanter.ember.platform.model.PlatformOperator operator() {
        return com.vanter.ember.platform.model.PlatformOperator.builder()
                .id(UUID.randomUUID()).email("operator@ember.local").build();
    }

    @Test
    void delete_softDeletesSuspendedRestaurantAndAudits() {
        Restaurant r = restaurant();
        r.setStatus(RestaurantStatus.SUSPENDED);
        var op = operator();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(op));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));

        platformRestaurantService.delete(r.getId(), "operator@ember.local");

        ArgumentCaptor<Restaurant> saved = ArgumentCaptor.forClass(Restaurant.class);
        org.mockito.Mockito.verify(restaurantRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(RestaurantStatus.DELETED);
        assertThat(saved.getValue().getDeletedAt()).isNotNull();
        assertThat(saved.getValue().getDeletedBy()).isEqualTo(op.getId());

        ArgumentCaptor<com.vanter.ember.platform.model.PlatformAuditLog> log =
                ArgumentCaptor.forClass(com.vanter.ember.platform.model.PlatformAuditLog.class);
        org.mockito.Mockito.verify(platformAuditLogRepository).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("RESTAURANT_DELETED");
        assertThat(log.getValue().getOldValue()).isEqualTo("SUSPENDED");
        assertThat(log.getValue().getNewValue()).isEqualTo("DELETED");
    }

    @Test
    void delete_rejectedWhenNotSuspended() {
        Restaurant r = restaurant(); // ACTIVE
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> platformRestaurantService.delete(r.getId(), "operator@ember.local"))
                .isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verify(restaurantRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(platformAuditLogRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restore_movesDeletedBackToSuspendedAndAudits() {
        Restaurant r = restaurant();
        r.setStatus(RestaurantStatus.DELETED);
        r.setDeletedAt(Instant.now());
        r.setDeletedBy(UUID.randomUUID());
        var op = operator();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(op));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(restaurantRepository.save(org.mockito.ArgumentMatchers.any(Restaurant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = platformRestaurantService.restore(r.getId(), "operator@ember.local");

        assertThat(result.getStatus()).isEqualTo(RestaurantStatus.SUSPENDED);
        ArgumentCaptor<Restaurant> saved = ArgumentCaptor.forClass(Restaurant.class);
        org.mockito.Mockito.verify(restaurantRepository).save(saved.capture());
        assertThat(saved.getValue().getDeletedAt()).isNull();
        assertThat(saved.getValue().getDeletedBy()).isNull();
        ArgumentCaptor<com.vanter.ember.platform.model.PlatformAuditLog> log =
                ArgumentCaptor.forClass(com.vanter.ember.platform.model.PlatformAuditLog.class);
        org.mockito.Mockito.verify(platformAuditLogRepository).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("RESTAURANT_RESTORED");
    }

    @Test
    void restore_rejectedWhenNotDeleted() {
        Restaurant r = restaurant();
        r.setStatus(RestaurantStatus.SUSPENDED);
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> platformRestaurantService.restore(r.getId(), "operator@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getAll_excludesDeletedByDefault() {
        Pageable pageable = PageRequest.of(0, 20);
        when(restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, pageable))
                .thenReturn(new PageImpl<>(List.of(restaurant())));

        platformRestaurantService.getAll(pageable, false);

        org.mockito.Mockito.verify(restaurantRepository).findByStatusNot(RestaurantStatus.DELETED, pageable);
        org.mockito.Mockito.verify(restaurantRepository, org.mockito.Mockito.never()).findAll(pageable);
    }

    @Test
    void getAll_includesDeletedWhenRequested() {
        Pageable pageable = PageRequest.of(0, 20);
        when(restaurantRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(restaurant())));

        platformRestaurantService.getAll(pageable, true);

        org.mockito.Mockito.verify(restaurantRepository).findAll(pageable);
    }

    @Test
    void updateStatus_rejectsDeletedTarget() {
        Restaurant r = restaurant();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> platformRestaurantService.updateStatus(
                r.getId(), RestaurantStatus.DELETED, "operator@ember.local"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatus_rejectedWhenCurrentlyDeleted() {
        Restaurant r = restaurant();
        r.setStatus(RestaurantStatus.DELETED);
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> platformRestaurantService.updateStatus(
                r.getId(), RestaurantStatus.ACTIVE, "operator@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    private PlatformRestaurantCreateRequest createRequest() {
        PlatformRestaurantCreateRequest request = new PlatformRestaurantCreateRequest();
        request.setName("Tenant Grill");
        request.setSlug("tenant-grill");
        request.setAdminName("Owner Admin");
        request.setAdminEmail("owner@tenant-grill.local");
        request.setAdminPassword("Str0ng!Pass");
        return request;
    }

    @Test
    void create_savesRestaurantAndAdminAndWritesAuditLog() {
        PlatformOperator operator = PlatformOperator.builder()
                .id(UUID.randomUUID())
                .email("operator@ember.local")
                .build();
        Restaurant saved = restaurant();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator));
        when(restaurantRepository.existsBySlug("tenant-grill")).thenReturn(false);
        when(userRepository.existsByEmail("owner@tenant-grill.local")).thenReturn(false);
        when(restaurantRepository.save(org.mockito.ArgumentMatchers.any(Restaurant.class))).thenReturn(saved);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("hashed");

        PlatformRestaurantSummaryResponse result = platformRestaurantService.create(
                createRequest(), "operator@ember.local");

        assertThat(result.getSlug()).isEqualTo("tenant-grill");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("owner@tenant-grill.local");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed");
        assertThat(savedUser.getRole()).isEqualTo(Role.ADMIN);
        assertThat(savedUser.getRestaurantId()).isEqualTo(saved);

        ArgumentCaptor<com.vanter.ember.platform.model.PlatformAuditLog> auditCaptor =
                ArgumentCaptor.forClass(com.vanter.ember.platform.model.PlatformAuditLog.class);
        org.mockito.Mockito.verify(platformAuditLogRepository).save(auditCaptor.capture());
        com.vanter.ember.platform.model.PlatformAuditLog logged = auditCaptor.getValue();
        assertThat(logged.getAction()).isEqualTo("RESTAURANT_CREATED");
        assertThat(logged.getRestaurantId()).isEqualTo(saved.getId());
        assertThat(logged.getOperatorEmail()).isEqualTo("operator@ember.local");
    }

    @Test
    void create_throwsWhenSlugTaken() {
        PlatformOperator operator = PlatformOperator.builder()
                .id(UUID.randomUUID())
                .email("operator@ember.local")
                .build();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator));
        when(restaurantRepository.existsBySlug("tenant-grill")).thenReturn(true);

        assertThatThrownBy(() -> platformRestaurantService.create(createRequest(), "operator@ember.local"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_throwsWhenAdminEmailTaken() {
        PlatformOperator operator = PlatformOperator.builder()
                .id(UUID.randomUUID())
                .email("operator@ember.local")
                .build();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator));
        when(restaurantRepository.existsBySlug("tenant-grill")).thenReturn(false);
        when(userRepository.existsByEmail("owner@tenant-grill.local")).thenReturn(true);

        assertThatThrownBy(() -> platformRestaurantService.create(createRequest(), "operator@ember.local"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_throwsWhenOperatorNotFound() {
        when(platformOperatorRepository.findByEmail("ghost@ember.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformRestaurantService.create(createRequest(), "ghost@ember.local"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void issueHubLicense_returnsSignedKeyAndWritesAuditLog() {
        PlatformOperator operator = PlatformOperator.builder()
                .id(UUID.randomUUID())
                .email("operator@ember.local")
                .build();
        UUID restaurantId = UUID.randomUUID();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator));
        when(restaurantRepository.existsById(restaurantId)).thenReturn(true);
        when(licenseIssuingService.issue(restaurantId)).thenReturn("signed-license-key");

        String result = platformRestaurantService.issueHubLicense(restaurantId, "operator@ember.local");

        assertThat(result).isEqualTo("signed-license-key");

        ArgumentCaptor<com.vanter.ember.platform.model.PlatformAuditLog> captor =
                ArgumentCaptor.forClass(com.vanter.ember.platform.model.PlatformAuditLog.class);
        org.mockito.Mockito.verify(platformAuditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("HUB_LICENSE_ISSUED");
        assertThat(captor.getValue().getRestaurantId()).isEqualTo(restaurantId);
    }

    @Test
    void issueHubLicense_throwsWhenRestaurantNotFound() {
        PlatformOperator operator = PlatformOperator.builder()
                .id(UUID.randomUUID())
                .email("operator@ember.local")
                .build();
        UUID restaurantId = UUID.randomUUID();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator));
        when(restaurantRepository.existsById(restaurantId)).thenReturn(false);

        assertThatThrownBy(() -> platformRestaurantService.issueHubLicense(restaurantId, "operator@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
