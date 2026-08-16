package com.vanter.ember.cashregister.event;

import java.util.UUID;

public record CashShiftOpened(UUID tenantId, Long shiftId) {}
