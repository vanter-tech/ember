package com.vanter.ember.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vanter.ember.platform.model.PlatformOperator;
import com.vanter.ember.platform.repository.PlatformOperatorRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class PlatformOperatorDetailsServiceTest {

    @Mock PlatformOperatorRepository platformOperatorRepository;
    @InjectMocks PlatformOperatorDetailsService platformOperatorDetailsService;

    @Test
    void loadUserByUsername_returnsUserDetailsForExistingOperator() {
        PlatformOperator operator = PlatformOperator.builder()
                .id(UUID.randomUUID())
                .name("Platform Admin")
                .email("operator@ember.local")
                .passwordHash("$2a$10$fakehashfakehashfakehashfakehashfakehashfakehash")
                .build();
        when(platformOperatorRepository.findByEmail("operator@ember.local"))
                .thenReturn(Optional.of(operator));

        UserDetails userDetails =
                platformOperatorDetailsService.loadUserByUsername("operator@ember.local");

        assertThat(userDetails.getUsername()).isEqualTo("operator@ember.local");
        assertThat(userDetails.getPassword()).isEqualTo(operator.getPasswordHash());
        assertThat(userDetails.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_PLATFORM_ADMIN");
    }

    @Test
    void loadUserByUsername_throwsWhenOperatorNotFound() {
        when(platformOperatorRepository.findByEmail("nobody@ember.local"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                platformOperatorDetailsService.loadUserByUsername("nobody@ember.local"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
