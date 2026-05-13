package com.vanter.ember.identity.dto;

import com.vanter.ember.identity.model.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull Role role) {}
