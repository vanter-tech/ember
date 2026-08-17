package com.vanter.ember.cashregister.event;

import java.util.UUID;

public record CashMovementRecorded(UUID tenantId, Long shiftId) {}
