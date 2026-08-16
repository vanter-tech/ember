package com.vanter.ember.identity.dto;

import java.math.BigDecimal;

/** Every field optional — a PATCH only applies the ones the caller actually sent. */
public record UpdateStaffProfileRequest(
        Boolean active,
        String jobTitle,
        String shift,
        String contractType,
        String location,
        BigDecimal efficiencyPercentage,
        BigDecimal pendingHours) {}
