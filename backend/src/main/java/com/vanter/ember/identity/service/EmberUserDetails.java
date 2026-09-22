package com.vanter.ember.identity.service;

import java.util.Collection;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * {@link org.springframework.security.core.userdetails.UserDetails} that also carries the
 * account's live {@code tokenVersion}, so {@code SecurityConfig#jwtAuthFilter} can reject a
 * token whose {@code ver} claim is stale — the same per-request DB-backed check it already does
 * for {@link #isEnabled()} (F-17).
 */
public class EmberUserDetails extends User {

    @Getter
    private final int tokenVersion;

    public EmberUserDetails(String username, String password,
            Collection<? extends GrantedAuthority> authorities, boolean enabled, int tokenVersion) {
        super(username, password, enabled, true, true, true, authorities);
        this.tokenVersion = tokenVersion;
    }
}
