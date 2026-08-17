package com.vanter.ember.identity.service;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmberUserDetailsServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks EmberUserDetailsService detailsService;

    @Test
    void loadUserByUsername_activeUserIsEnabled() {
        User user = User.builder()
                .email("ana@test.com").passwordHash("hashed").role(Role.WAITER).active(true).build();
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));

        UserDetails details = detailsService.loadUserByUsername("ana@test.com");

        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsername_inactiveUserIsDisabled() {
        User user = User.builder()
                .email("ana@test.com").passwordHash("hashed").role(Role.WAITER).active(false).build();
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));

        UserDetails details = detailsService.loadUserByUsername("ana@test.com");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsername_throwsWhenUserNotFound() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> detailsService.loadUserByUsername("missing@test.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
