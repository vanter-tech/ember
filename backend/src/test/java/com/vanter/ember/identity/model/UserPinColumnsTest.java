package com.vanter.ember.identity.model;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TenantIdentifierResolver.class)
class UserPinColumnsTest {

    @Autowired UserRepository userRepository;

    @Test
    void pinHashAndUpdatedAt_roundTrip() {
        User u = User.builder()
                .name("Waiter One").email("w1@test.com")
                .passwordHash("x").role(Role.WAITER)
                .pinHash("$2a$10$abcdefghijklmnopqrstuv")
                .pinUpdatedAt(Instant.parse("2026-09-03T10:00:00Z"))
                .build();

        User saved = userRepository.saveAndFlush(u);
        userRepository.findById(saved.getId()).ifPresent(found -> {
            assertThat(found.getPinHash()).isEqualTo("$2a$10$abcdefghijklmnopqrstuv");
            assertThat(found.getPinUpdatedAt()).isEqualTo(Instant.parse("2026-09-03T10:00:00Z"));
        });
    }
}
