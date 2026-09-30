package com.vanter.ember.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why an administrator is closing a table that nobody can charge or close any more. */
public record ForceCloseTableRequest(@NotBlank @Size(max = 255) String reason) {}
