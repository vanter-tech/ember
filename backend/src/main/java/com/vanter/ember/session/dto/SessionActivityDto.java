package com.vanter.ember.session.dto;

import com.vanter.ember.session.model.SessionActivity;

import java.time.LocalDateTime;

public record SessionActivityDto(
        SessionActivity.Type type,
        String itemName,
        String participantName,
        LocalDateTime timestamp
) {
}
