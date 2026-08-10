package com.vanter.ember.session.dto;

import com.vanter.ember.session.model.OrderItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderItemDto(
        String id,
        String name,
        BigDecimal price,
        String participantName,
        String participantId,
        OrderItemStatus status,
        LocalDateTime addedAt
) {
}
