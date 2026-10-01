package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkTableRequest(@NotNull UUID tableId) {}
