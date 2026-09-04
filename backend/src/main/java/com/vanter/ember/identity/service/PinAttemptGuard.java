package com.vanter.ember.identity.service;

import com.vanter.ember.identity.exception.PinLockedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Best-effort brute-force throttle for {@code POST /auth/login/pin}. In-memory and node-local:
 * a PIN is low-entropy, and the password path (with its own protections) remains the fallback,
 * so a counter that resets on restart is acceptable for this single-node modular monolith.
 */
@Component
public class PinAttemptGuard {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempt(int count, Instant windowStart) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public PinAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    public void assertNotLocked(String email) {
        Attempt a = attempts.get(key(email));
        if (a != null && a.count() >= MAX_FAILURES && !windowExpired(a)) {
            throw new PinLockedException();
        }
    }

    public void recordFailure(String email) {
        attempts.compute(key(email), (k, a) -> {
            Instant nowInstant = clock.instant();
            if (a == null || windowExpired(a)) {
                return new Attempt(1, nowInstant);
            }
            return new Attempt(a.count() + 1, a.windowStart());
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private boolean windowExpired(Attempt a) {
        return a.windowStart().plus(WINDOW).isBefore(clock.instant());
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
