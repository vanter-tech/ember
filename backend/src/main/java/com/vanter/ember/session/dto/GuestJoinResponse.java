package com.vanter.ember.session.dto;

import com.vanter.ember.session.model.Session;

import java.util.UUID;

/**
 * Guest join result: the {@link JoinSessionResponse} payload plus the identity fields a login
 * would return. A guest never logs in, so without {@code userId}/{@code role}/{@code name} the
 * client has no auth identity and its CUSTOMER route guard bounces it to /login.
 */
public record GuestJoinResponse(
        Session session, String token, String userId, UUID restaurantId, String name, String role) {}
