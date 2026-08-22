package com.vanter.ember.printing.event;

import java.util.UUID;

/**
 * Published by {@code PrintAgentChannelInterceptor} on a successful CONNECT, instead of it
 * calling {@code PrintDispatchService} directly — a direct constructor dependency there closes
 * a circular bean cycle back through {@code SimpMessagingTemplate} (which itself needs every
 * {@code WebSocketMessageBrokerConfigurer}, including the one that owns this interceptor, to
 * exist first). Routing through {@code ApplicationEventPublisher} defers the call to runtime,
 * breaking the cycle — same internal-event convention as the rest of this codebase.
 */
public record PrintAgentConnected(UUID agentId) {}
