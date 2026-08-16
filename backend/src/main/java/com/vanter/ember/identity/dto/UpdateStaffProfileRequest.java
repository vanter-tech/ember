package com.vanter.ember.identity.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Every field optional — a PATCH only applies the ones the caller actually sent. */
public record UpdateStaffProfileRequest(
        Boolean active,
        @Size(max = 255) String jobTitle,
        @Size(max = 255) String shift,
        @Size(max = 255) String contractType,
        @Size(max = 255) String location,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal efficiencyPercentage,
        @DecimalMin("0") @Digits(integer = 4, fraction = 2) BigDecimal pendingHours) {}
