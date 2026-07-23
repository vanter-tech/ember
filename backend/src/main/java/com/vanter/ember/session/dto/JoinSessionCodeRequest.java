package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinSessionCodeRequest(
        @NotBlank String joinCode
) {
}
