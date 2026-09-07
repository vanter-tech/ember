package com.vanter.ember.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class GuestUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock GuestNameGenerator nameGenerator;
    @InjectMocks GuestUserService service;

    @Test
    void createGuest_withBlankName_usesGeneratedName_andFlagsGuest() {
        when(nameGenerator.next()).thenReturn("Puma Veloz");
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User guest = service.createGuest("   ");

        assertThat(guest.getName()).isEqualTo("Puma Veloz");
        assertThat(guest.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(guest.getGuest()).isTrue();
        assertThat(guest.getActive()).isTrue();
        assertThat(guest.getRestaurantId()).isNull();
        assertThat(guest.getEmail()).startsWith("guest+").endsWith("@guests.ember.local");
        assertThat(guest.getPasswordHash()).isEqualTo("$2a$hash");
    }

    @Test
    void createGuest_withProvidedName_trimsAndUsesIt() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User guest = service.createGuest("  Ana  ");

        assertThat(guest.getName()).isEqualTo("Ana");
    }
}
