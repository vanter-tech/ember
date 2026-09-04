package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferTableRequest(@NotBlank String targetWaiterId) {}
