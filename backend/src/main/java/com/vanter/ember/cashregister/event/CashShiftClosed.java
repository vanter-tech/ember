package com.vanter.ember.cashregister.event;

import java.util.UUID;

public record CashShiftClosed(UUID tenantId, Long shiftId) {}
