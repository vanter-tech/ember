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
 * {@link PlatformOperatorRepository} instead of the tenant {@code UserRepository}. There is only
 * one operator "role" today, so it is granted a single fixed {@code PLATFORM_ADMIN} authority
 * rather than reading a role column that does not exist on {@code PlatformOperator}.
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
                        .roles("PLATFORM_ADMIN")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Platform operator not found: " + email));
    }
}
