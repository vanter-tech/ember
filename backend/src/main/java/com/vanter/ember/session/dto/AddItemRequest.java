package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotNull;

public record AddItemRequest(@NotNull Long menuItemId) {}
