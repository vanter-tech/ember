package com.vanter.ember.kitchen.dto;

import com.vanter.ember.session.model.OrderItemStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateItemStatusRequest(@NotNull OrderItemStatus status) {}
