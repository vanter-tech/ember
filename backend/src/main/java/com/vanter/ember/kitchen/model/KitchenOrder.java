package com.vanter.ember.kitchen.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "kitchen_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenOrder {

    @Id
    private String id;

    private String sessionId;
    private int tableNumber;
    private LocalDateTime createdAt;

    @Builder.Default
    private List<KitchenItem> items = new ArrayList<>();
}
