package com.vanter.ember.printing.dto;

import jakarta.validation.constraints.NotBlank;

public record PairRequest(@NotBlank String code) {}
