package com.vanter.ember.printing.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Best-effort brute-force throttle for {@code POST /printing/agents/pair}, keyed by client IP.
 * In-memory / node-local — acceptable for this single-node monolith, same reasoning as
 * {@link com.vanter.ember.identity.service.PinAttemptGuard}. A pairing code is a 10-char
 * base32 token (~50 bits) with a 15-minute TTL; this just closes the "hammer /pair" hole.
 */
@Component
public class PairAttemptGuard {

    private static final int MAX_FAILURES = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempt(int count, Instant windowStart) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public PairAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    public void assertNotLocked(String ip) {
        Attempt a = attempts.get(ip);
        if (a != null && a.count() >= MAX_FAILURES && !windowExpired(a)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many pairing attempts");
        }
    }

    public void recordFailure(String ip) {
        attempts.compute(ip, (k, a) -> {
            Instant now = clock.instant();
            if (a == null || windowExpired(a)) {
                return new Attempt(1, now);
            }
            return new Attempt(a.count() + 1, a.windowStart());
        });
    }

    public void recordSuccess(String ip) {
        attempts.remove(ip);
    }

    private boolean windowExpired(Attempt a) {
        return a.windowStart().plus(WINDOW).isBefore(clock.instant());
    }
}
