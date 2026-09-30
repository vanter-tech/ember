package com.vanter.ember.cashregister.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualOpenRequest(@NotBlank @Size(max = 255) String reason) {}
