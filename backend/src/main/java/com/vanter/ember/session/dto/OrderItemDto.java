package com.vanter.ember.session.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderItemDto(
        String id,
        String name,
        BigDecimal price,
        String participantName,
        LocalDateTime addedAt
) {
}
