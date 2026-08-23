package com.vanter.ember.session.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    private String id;
    private Long itemId;
    private String name;
    private BigDecimal price;
    private String participantId;
    private String participantName;
    private OrderItemStatus status;
    @Builder.Default
    private java.util.List<SelectedModifier> modifiers = new java.util.ArrayList<>();
    private LocalDateTime addedAt;
}
