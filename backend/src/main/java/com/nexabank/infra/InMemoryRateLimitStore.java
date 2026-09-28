package com.nexabank.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-instance fixed-window quota store (default when Redis is off).
 * Thread-safe; expired windows are reaped opportunistically to bound memory.
 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRateLimitStore implements RateLimitStore {

    private static final int MAX_BUCKETS = 50_000;

    private final Map<String, Window> buckets = new ConcurrentHashMap<>();

    @Override
    public Acquisition tryAcquire(String key, int maxRequests, Duration window) {
        Instant now = Instant.now();
        Window result = buckets.compute(key, (k, existing) -> {
            if (existing == null || !existing.expiresAt.isAfter(now)) {
                return new Window(1, now.plus(window));
            }
            return new Window(existing.count + 1, existing.expiresAt);
        });
        reapIfNeeded(now);
        if (result.count <= maxRequests) {
            return new Acquisition(true, 0);
        }
        long retryAfter = Math.max(1, Duration.between(now, result.expiresAt).getSeconds());
        return new Acquisition(false, retryAfter);
    }

    /** Visible for tests. */
    public int bucketCount() {
        return buckets.size();
    }

    private void reapIfNeeded(Instant now) {
        if (buckets.size() < MAX_BUCKETS) {
            return;
        }
        Iterator<Map.Entry<String, Window>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            if (!it.next().getValue().expiresAt.isAfter(now)) {
                it.remove();
            }
        }
    }

    private record Window(long count, Instant expiresAt) {
    }
}
