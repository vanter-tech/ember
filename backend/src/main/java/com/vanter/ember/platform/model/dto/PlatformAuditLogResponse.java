package com.vanter.ember.platform.model.dto;

import com.vanter.ember.platform.model.PlatformAuditLog;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformAuditLogResponse {
    private UUID id;
    private UUID operatorId;
    private String operatorEmail;
    private UUID restaurantId;
    private String action;
    private String oldValue;
    private String newValue;
    private Instant createdAt;

    public static PlatformAuditLogResponse from(PlatformAuditLog entry) {
        return PlatformAuditLogResponse.builder()
                .id(entry.getId())
                .operatorId(entry.getOperatorId())
                .operatorEmail(entry.getOperatorEmail())
                .restaurantId(entry.getRestaurantId())
                .action(entry.getAction())
                .oldValue(entry.getOldValue())
                .newValue(entry.getNewValue())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
