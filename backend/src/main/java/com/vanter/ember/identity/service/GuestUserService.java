package com.vanter.ember.identity.service;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GuestNameGenerator nameGenerator;

    /**
     * Create and persist a throwaway CUSTOMER identity for a walk-in diner. The email is
     * synthetic and unique; the password is a random value nobody knows, so {@code POST
     * /auth/login} can never authenticate a guest — they are resumed only via their stored JWT.
     */
    @Transactional
    public User createGuest(String requestedName) {
        String name = requestedName != null && !requestedName.isBlank()
                ? requestedName.trim()
                : nameGenerator.next();

        User guest = User.builder()
                .name(name)
                .email("guest+" + UUID.randomUUID() + "@guests.ember.local")
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.CUSTOMER)
                .guest(true)
                .build();

        return userRepository.save(guest);
    }
}
