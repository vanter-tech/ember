package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AddWaiterItemRequest(
        @NotNull Long menuItemId,
        List<Long> selectedOptionIds,
        String participantName) {

    public AddWaiterItemRequest {
        if (selectedOptionIds == null) {
            selectedOptionIds = List.of();
        }
    }
}
