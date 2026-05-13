package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinSessionRequest(
        @NotBlank String qrToken,
        @NotBlank @Size(max = 50) String userName
) {}
