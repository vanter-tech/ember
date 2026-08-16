package com.vanter.ember.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.platform.model.PlatformAuditLog;
import com.vanter.ember.platform.repository.PlatformAuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PlatformAuditLogServiceTest {

    @Mock PlatformAuditLogRepository platformAuditLogRepository;
    @InjectMocks PlatformAuditLogService platformAuditLogService;

    private PlatformAuditLog entry(UUID restaurantId) {
        return PlatformAuditLog.builder()
                .id(UUID.randomUUID())
                .operatorId(UUID.randomUUID())
                .operatorEmail("operator@ember.local")
                .restaurantId(restaurantId)
                .action("RESTAURANT_STATUS_UPDATED")
                .oldValue("ACTIVE")
                .newValue("SUSPENDED")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void getAuditLog_withoutRestaurantId_delegatesToFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        PlatformAuditLog entry = entry(UUID.randomUUID());
        when(platformAuditLogRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(entry)));

        var result = platformAuditLogService.getAuditLog(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("RESTAURANT_STATUS_UPDATED");
        verify(platformAuditLogRepository, never()).findByRestaurantId(any(), any());
    }

    @Test
    void getAuditLog_withRestaurantId_delegatesToFindByRestaurantId() {
        UUID restaurantId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        PlatformAuditLog entry = entry(restaurantId);
        when(platformAuditLogRepository.findByRestaurantId(restaurantId, pageable))
                .thenReturn(new PageImpl<>(List.of(entry)));

        var result = platformAuditLogService.getAuditLog(restaurantId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRestaurantId()).isEqualTo(restaurantId);
    }
}
