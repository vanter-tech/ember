package com.vanter.ember.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record VoidBillRequest(@NotBlank String reason) {}
