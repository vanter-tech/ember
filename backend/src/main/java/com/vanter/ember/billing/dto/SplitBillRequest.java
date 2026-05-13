package com.vanter.ember.billing.dto;

import com.vanter.ember.billing.model.SplitMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SplitBillRequest(@NotNull SplitMethod splitMethod, @Min(1) Integer participantCount) {}
