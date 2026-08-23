package com.vanter.ember.catalog.model.dto;

import jakarta.validation.constraints.NotNull;

public record ModifierGroupAssignment(@NotNull Long groupId, int displayOrder) {}
