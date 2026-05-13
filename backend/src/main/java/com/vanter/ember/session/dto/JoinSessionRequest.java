package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinSessionRequest(
        @NotBlank String qrToken,
        @NotBlank String userName
) {}
