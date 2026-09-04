package com.vanter.ember.config;

import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final SessionService sessionService;

    static final String TENANT_SESSION_ATTRIBUTE = "tenantId";

    // Tenant-wide staff broadcasts: "/topic/<segment>/<tenantId>". Matched against a UUID, not
    // just any string, so it never accidentally swallows an unrelated future "/topic/<segment>/*"
    // destination shaped differently.
    private static final Pattern TENANT_TOPIC = Pattern.compile(
            "^/topic/(waiter|kitchen|cash-register|inventory)/([0-9a-fA-F-]{36})$");
    // Per-session broadcasts: "/topic/session/<sessionId>".
    private static final Pattern SESSION_TOPIC = Pattern.compile("^/topic/session/(.+)$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // The client inbound channel is shared with the print-agent-only /ws/print-agent
        // endpoint (Spring merges every WebSocketMessageBrokerConfigurer's interceptors onto one
        // channel) — a print-agent session authenticates via PrintAgentChannelInterceptor
        // instead, never through this tenant-user path.
        if (isPrintAgentEndpoint(accessor)) {
            return message;
        }

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            bindTenantFromSession(accessor);
            // QA_SIMULATION_REPORT.md E-01: preSend used to authenticate CONNECT only and let
            // every later frame through unchecked — a customer of tenant B could SUBSCRIBE to
            // tenant A's /topic/waiter/{tenantA} (or any other tenant's/table's /topic/session/{id})
            // and receive its live broadcasts. Reject an unauthorized SUBSCRIBE outright; every
            // other frame (SEND/DISCONNECT/heartbeats) is unaffected.
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                    && !isAuthorizedSubscription(accessor)) {
                throw new MessageDeliveryException(message, "Not authorized to subscribe to this destination");
            }
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessageDeliveryException(message, "Missing Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtService.isTokenValid(token)) {
            throw new MessageDeliveryException(message, "Invalid or expired token");
        }

        String email = jwtService.extractSubject(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        accessor.setUser(auth);

        UUID tenantId = jwtService.extractTenantId(token);
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put(TENANT_SESSION_ATTRIBUTE, tenantId);
        }
        TenantContextHolder.setTenantId(tenantId);

        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        TenantContextHolder.clear();
    }

    private void bindTenantFromSession(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }
        Object tenantId = sessionAttributes.get(TENANT_SESSION_ATTRIBUTE);
        if (tenantId instanceof UUID uuid) {
            TenantContextHolder.setTenantId(uuid);
        }
    }

    private static boolean isPrintAgentEndpoint(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        return sessionAttributes != null
                && WebSocketSessionAttributes.PRINT_AGENT_ENDPOINT.equals(
                        sessionAttributes.get(WebSocketSessionAttributes.ENDPOINT_ATTRIBUTE));
    }

    /**
     * Destinations this interceptor doesn't recognize (e.g. {@code /user/queue/**}) are left
     * alone — this method only tightens the two shapes known to leak tenant/session data.
     */
    private boolean isAuthorizedSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return true;
        }

        Matcher tenantMatch = TENANT_TOPIC.matcher(destination);
        if (tenantMatch.matches()) {
            UUID topicTenant;
            try {
                topicTenant = UUID.fromString(tenantMatch.group(2));
            } catch (IllegalArgumentException ex) {
                return false;
            }
            UUID boundTenant = TenantContextHolder.getTenantId();
            if (boundTenant == null || !boundTenant.equals(topicTenant)) {
                return false;
            }
            return "kitchen".equals(tenantMatch.group(1))
                    ? hasAnyRole(accessor, "KITCHEN", "ADMIN")
                    : hasAnyRole(accessor, "WAITER", "ADMIN");
        }

        Matcher sessionMatch = SESSION_TOPIC.matcher(destination);
        if (sessionMatch.matches()) {
            return isAuthorizedSessionTopic(sessionMatch.group(1), accessor);
        }

        return true;
    }

    /**
     * A session's topic is visible to any staff member of its own tenant (mirrors
     * {@code GET /sessions/{id}}'s existing REST authorization) or to a participant of that
     * specific session. {@link SessionService#findById} is tenant-scoped, so a session belonging
     * to a different tenant than the one bound to this STOMP session is indistinguishable from a
     * nonexistent one — exactly the guarantee this check needs.
     */
    private boolean isAuthorizedSessionTopic(String sessionId, StompHeaderAccessor accessor) {
        try {
            sessionService.findById(sessionId);
        } catch (ResourceNotFoundException ex) {
            return false;
        }
        if (hasAnyRole(accessor, "WAITER", "ADMIN", "KITCHEN")) {
            return true;
        }
        String email = principalName(accessor);
        return email != null && sessionService.isParticipant(sessionId, email);
    }

    private static boolean hasAnyRole(StompHeaderAccessor accessor, String... roles) {
        Principal user = accessor.getUser();
        if (!(user instanceof UsernamePasswordAuthenticationToken auth)) {
            return false;
        }
        Set<String> required = Arrays.stream(roles).map(r -> "ROLE_" + r).collect(Collectors.toSet());
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(required::contains);
    }

    private static String principalName(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        return user != null ? user.getName() : null;
    }
}
