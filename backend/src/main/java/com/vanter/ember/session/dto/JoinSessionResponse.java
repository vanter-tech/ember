package com.vanter.ember.session.dto;

import com.vanter.ember.session.model.Session;

/**
 * The joined session plus a token re-scoped to that session's restaurant. A customer's login
 * token carries no tenant until they join a table, so the client must swap to this one for
 * every follow-up call (menu, items, confirm).
 */
public record JoinSessionResponse(Session session, String token) {}
