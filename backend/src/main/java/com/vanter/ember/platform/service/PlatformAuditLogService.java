package com.vanter.ember.platform.service;

import com.vanter.ember.platform.model.PlatformAuditLog;
import com.vanter.ember.platform.model.dto.PlatformAuditLogResponse;
import com.vanter.ember.platform.repository.PlatformAuditLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Read-only access to {@link com.vanter.ember.platform.model.PlatformAuditLog} (EMB-PC-09).
 * {@code restaurantId} is an optional filter — omitting it lists every operator action platform-wide.
 */
@Service
@RequiredArgsConstructor
public class PlatformAuditLogService {

    private final PlatformAuditLogRepository platformAuditLogRepository;

    public Page<PlatformAuditLogResponse> getAuditLog(UUID restaurantId, Pageable pageable) {
        Page<PlatformAuditLog> page = restaurantId != null
                ? platformAuditLogRepository.findByRestaurantId(restaurantId, pageable)
                : platformAuditLogRepository.findAll(pageable);
        return page.map(PlatformAuditLogResponse::from);
    }
}
