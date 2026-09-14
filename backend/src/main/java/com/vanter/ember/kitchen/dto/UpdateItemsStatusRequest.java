package com.vanter.ember.kitchen.dto;

import com.vanter.ember.session.model.OrderItemStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateItemsStatusRequest(@NotEmpty List<String> itemIds, @NotNull OrderItemStatus status) {}
