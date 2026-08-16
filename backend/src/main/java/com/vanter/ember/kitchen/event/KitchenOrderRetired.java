package com.vanter.ember.kitchen.event;

import java.util.UUID;

public record KitchenOrderRetired(
        UUID tenantId,
        String sessionId
) {}
