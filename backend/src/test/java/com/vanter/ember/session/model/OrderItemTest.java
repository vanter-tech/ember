package com.vanter.ember.session.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    void orderItem_hasAllRequiredFields() {
        LocalDateTime now = LocalDateTime.now();

        OrderItem item = OrderItem.builder()
                .itemId(10L)
                .name("Tacos")
                .price(new BigDecimal("12.50"))
                .participantName("Alice")
                .participantId("user-1")
                .status(OrderItemStatus.PENDING)
                .addedAt(now)
                .build();

        assertThat(item.getItemId()).isEqualTo(10L);
        assertThat(item.getName()).isEqualTo("Tacos");
        assertThat(item.getPrice()).isEqualByComparingTo("12.50");
        assertThat(item.getParticipantName()).isEqualTo("Alice");
        assertThat(item.getParticipantId()).isEqualTo("user-1");
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PENDING);
        assertThat(item.getAddedAt()).isEqualTo(now);
    }

    @Test
    void orderItemStatus_hasAllValues() {
        assertThat(OrderItemStatus.values()).containsExactlyInAnyOrder(
                OrderItemStatus.PENDING,
                OrderItemStatus.PREPARING,
                OrderItemStatus.READY,
                OrderItemStatus.DELIVERED);
    }

    @Test
    void orderItem_defaultStatusIsPending() {
        OrderItem item = OrderItem.builder()
                .itemId(1L).name("Burger").price(BigDecimal.TEN)
                .participantId("user-1").participantName("Bob")
                .addedAt(LocalDateTime.now())
                .build();

        assertThat(item.getStatus()).isNull();
    }
}
