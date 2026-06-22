package lk.customs.rms.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login throttle — application-layer defense-in-depth alongside the reverse-proxy rate
 * limit documented in DEPLOYMENT.md.
 *
 * <p>Keyed by client IP so a single source brute-forcing many passwords is blocked, and a
 * successful login clears the counter. It is intentionally NOT keyed by username alone: that would
 * let an attacker lock out a legitimate user simply by spamming their username with wrong passwords.
 *
 * <p>Suitable for the single-instance deployment this app targets. If the backend is ever scaled
 * horizontally, move this state to a shared store (e.g. Redis).
 */
@Service
public class LoginAttemptService {

    // Safety valve so a flood of distinct keys (e.g. spoofed X-Forwarded-For) cannot grow the map
    // without bound; expired entries are swept once this many keys are tracked.
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final int maxFailedAttempts;
    private final long windowMs;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.login.max-failed-attempts:10}") int maxFailedAttempts,
            @Value("${app.login.block-minutes:15}") long blockMinutes) {
        this.maxFailedAttempts = maxFailedAttempts;
        this.windowMs = blockMinutes * 60_000L;
    }

    public boolean isBlocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        if (isExpired(attempt)) {
            attempts.remove(key, attempt);
            return false;
        }
        return attempt.count >= maxFailedAttempts;
    }

    public void recordFailure(String key) {
        if (attempts.size() > MAX_TRACKED_KEYS) {
            attempts.values().removeIf(this::isExpired);
        }
        long now = System.currentTimeMillis();
        attempts.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > windowMs) {
                return new Attempt(now);
            }
            existing.count++;
            return existing;
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    public long blockSeconds() {
        return windowMs / 1000L;
    }

    private boolean isExpired(Attempt attempt) {
        return System.currentTimeMillis() - attempt.windowStart > windowMs;
    }

    private static final class Attempt {
        private final long windowStart;
        private int count;

        private Attempt(long windowStart) {
            this.windowStart = windowStart;
            this.count = 1;
        }
    }
}
