package com.vanter.ember.identity.dto;

import com.vanter.ember.identity.model.User;

public record WaiterSummary(String id, String name, String email) {
    public static WaiterSummary from(User u) {
        return new WaiterSummary(u.getId(), u.getName(), u.getEmail());
    }
}
