package com.vanter.ember.platform.service;

import com.vanter.ember.platform.repository.PlatformOperatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads {@link com.vanter.ember.platform.model.PlatformOperator} rows for platform auth
 * (EMB-PC-04's {@code /platform/**} filter chain), mirroring
 * {@link com.vanter.ember.identity.service.EmberUserDetailsService} but over
 * {@link PlatformOperatorRepository} instead of the tenant {@code UserRepository}. Grants the
 * operator's real {@code role} column (F-14) as the Spring Security authority, so
 * {@code @PreAuthorize} on the {@code /platform/**} controllers can tell {@code SUPER_ADMIN} apart
 * from a read-only {@code SUPPORT} account.
 */
@Service
@RequiredArgsConstructor
public class PlatformOperatorDetailsService implements UserDetailsService {

    private final PlatformOperatorRepository platformOperatorRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return platformOperatorRepository.findByEmail(email)
                .map(operator -> org.springframework.security.core.userdetails.User.builder()
                        .username(operator.getEmail())
                        .password(operator.getPasswordHash())
                        .roles(operator.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Platform operator not found: " + email));
    }
}
