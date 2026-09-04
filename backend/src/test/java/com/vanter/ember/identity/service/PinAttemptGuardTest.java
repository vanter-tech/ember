package com.vanter.ember.identity.service;

import com.vanter.ember.identity.exception.PinLockedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class PinAttemptGuardTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-03T12:00:00Z"));

    private PinAttemptGuard newGuard() {
        Clock movable = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return now.get(); }
        };
        return new PinAttemptGuard(movable);
    }

    @Test
    void locksAfterFiveFailuresInWindow() {
        PinAttemptGuard guard = newGuard();
        for (int i = 0; i < 5; i++) guard.recordFailure("w1@test.com");
        assertThatThrownBy(() -> guard.assertNotLocked("w1@test.com"))
                .isInstanceOf(PinLockedException.class);
    }

    @Test
    void successClearsCounter() {
        PinAttemptGuard guard = newGuard();
        for (int i = 0; i < 4; i++) guard.recordFailure("w1@test.com");
        guard.recordSuccess("w1@test.com");
        guard.recordFailure("w1@test.com");
        assertThatCode(() -> guard.assertNotLocked("w1@test.com")).doesNotThrowAnyException();
    }

    @Test
    void lockExpiresAfterWindow() {
        PinAttemptGuard guard = newGuard();
        for (int i = 0; i < 5; i++) guard.recordFailure("w1@test.com");
        now.set(now.get().plus(Duration.ofMinutes(16)));
        assertThatCode(() -> guard.assertNotLocked("w1@test.com")).doesNotThrowAnyException();
    }

    @Test
    void isCaseInsensitiveOnEmail() {
        PinAttemptGuard guard = newGuard();
        for (int i = 0; i < 5; i++) guard.recordFailure("W1@Test.com");
        assertThatThrownBy(() -> guard.assertNotLocked("w1@test.com"))
                .isInstanceOf(PinLockedException.class);
    }
}
