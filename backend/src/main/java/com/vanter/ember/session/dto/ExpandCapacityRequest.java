package com.vanter.ember.session.dto;

import jakarta.validation.constraints.Min;

public record ExpandCapacityRequest(
        @Min(1) int additional
) {}
