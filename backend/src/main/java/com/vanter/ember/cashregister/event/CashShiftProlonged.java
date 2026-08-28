package com.vanter.ember.cashregister.event;

import java.util.UUID;

public record CashShiftProlonged(UUID tenantId, Long shiftId) {}
