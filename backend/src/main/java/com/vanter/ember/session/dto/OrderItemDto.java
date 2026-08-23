package com.vanter.ember.session.dto;

import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.SelectedModifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderItemDto(
        String id,
        String name,
        BigDecimal price,
        String participantName,
        String participantId,
        OrderItemStatus status,
        List<SelectedModifier> modifiers,
        LocalDateTime addedAt
) {
}
