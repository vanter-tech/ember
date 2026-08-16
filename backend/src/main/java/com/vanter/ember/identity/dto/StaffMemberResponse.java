package com.vanter.ember.identity.dto;

import com.vanter.ember.identity.model.Role;
import java.math.BigDecimal;
import java.time.Instant;

public record StaffMemberResponse(
        String id,
        String name,
        String email,
        Role role,
        Instant createdAt,
        Boolean active,
        String jobTitle,
        String shift,
        String contractType,
        String location,
        BigDecimal efficiencyPercentage,
        BigDecimal pendingHours) {}
