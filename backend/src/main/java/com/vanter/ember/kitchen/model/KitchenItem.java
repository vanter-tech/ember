package com.vanter.ember.kitchen.model;

import com.vanter.ember.session.model.OrderItemStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenItem {

    private String itemId;
    private String name;
    private String participantName;
    private OrderItemStatus status;
    private LocalDateTime updatedAt;
}
