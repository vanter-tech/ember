package com.vanter.ember.kitchen.event;

import java.util.UUID;

public record KitchenItemRemoved(
        UUID tenantId,
        String sessionId,
        String itemId
) {}
