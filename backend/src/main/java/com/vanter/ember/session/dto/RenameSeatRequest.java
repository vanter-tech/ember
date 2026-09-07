package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rename the seat currently named {@code from} to {@code to}. */
public record RenameSeatRequest(
        @NotBlank String from,
        @NotBlank @Size(max = 50) String to) {
}
