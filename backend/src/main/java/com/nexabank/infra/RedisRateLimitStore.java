package com.nexabank.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Shared fixed-window quota store backed by Redis (active when
 * {@code app.redis.enabled=true}), so limits hold across app instances.
 * First hit in a window sets the key TTL; later hits only increment.
 * A Redis failure fails OPEN (allows the request) and is logged — rate
 * limiting must never take down money movement; abuse protection degrades,
 * availability does not.
 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    private final StringRedisTemplate redis;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Acquisition tryAcquire(String key, int maxRequests, Duration window) {
        String bucket = "nexabank:ratelimit:" + key;
        try {
            Long count = redis.opsForValue().increment(bucket);
            if (count == null) {
                return new Acquisition(true, 0);
            }
            if (count == 1) {
                redis.expire(bucket, window.toMillis(), TimeUnit.MILLISECONDS);
            }
            if (count <= maxRequests) {
                return new Acquisition(true, 0);
            }
            Long ttl = redis.getExpire(bucket, TimeUnit.SECONDS);
            return new Acquisition(false, ttl == null || ttl < 1 ? 1 : ttl);
        } catch (RuntimeException ex) {
            // Fail open: log and allow. Redis is never authoritative here.
            log.warn("Rate-limit Redis unavailable, failing open for bucket hash {} correlationId={}",
                    Integer.toHexString(bucket.hashCode()), CorrelationIdFilter.current(), ex);
            return new Acquisition(true, 0);
        }
    }
}
