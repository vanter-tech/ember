package com.vanter.ember.identity.service;

import com.vanter.ember.identity.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * The default {@code UserDetailsService}: {@code @Primary} so every existing by-type injection
 * site (e.g. {@code SecurityConfig}, {@code JwtChannelInterceptor}) keeps resolving to this one
 * unambiguously now that {@link com.vanter.ember.platform.service.PlatformOperatorDetailsService}
 * also implements the interface. EMB-PC-04's platform filter chain must inject
 * {@code PlatformOperatorDetailsService} by its concrete type rather than by the
 * {@code UserDetailsService} interface, to stay unambiguous without relying on this default.
 */
@Service
@Primary
@RequiredArgsConstructor
public class EmberUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(user -> new EmberUserDetails(
                        user.getEmail(),
                        user.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                        Boolean.TRUE.equals(user.getActive()),
                        user.getTokenVersion()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
