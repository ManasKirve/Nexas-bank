package com.nexabank.infra;

import java.time.Duration;

/**
 * Fixed-window rate-limit quota store.
 *
 * <p>Two implementations: in-memory (default, single instance) and Redis
 * (shared across instances when {@code app.redis.enabled=true}). The
 * financial safety mechanism remains idempotency — rate limiting only
 * sheds abusive load.</p>
 */
public interface RateLimitStore {

    /** Result of one quota acquisition attempt. */
    record Acquisition(boolean allowed, long retryAfterSeconds) {
    }

    /**
     * Atomically consumes one unit of quota.
     *
     * @param key         quota bucket (already namespaced by caller)
     * @param maxRequests units allowed per window
     * @param window      window length
     * @return whether the call may proceed + seconds until reset when denied
     */
    Acquisition tryAcquire(String key, int maxRequests, Duration window);
}
