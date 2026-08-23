package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AddItemRequest(@NotNull Long menuItemId, List<Long> selectedOptionIds) {

    public AddItemRequest {
        if (selectedOptionIds == null) {
            selectedOptionIds = List.of();
        }
    }
}
