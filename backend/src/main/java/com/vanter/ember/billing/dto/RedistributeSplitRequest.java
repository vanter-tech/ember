package com.vanter.ember.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record RedistributeSplitRequest(@NotBlank String departingParticipantName) {}
