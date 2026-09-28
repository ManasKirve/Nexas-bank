package com.nexabank.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Cache + Redis wiring.
 *
 * <p>Redis is strictly non-authoritative: it holds customer-profile copies
 * and rate-limit windows only. PostgreSQL remains the source of truth for
 * balances, ledger rows, transfers and fraud history.</p>
 *
 * <ul>
 *   <li>{@code app.redis.enabled=true} (dev/compose): Lettuce connection +
 *   Redis-backed caches with per-cache TTLs.</li>
 *   <li>default (incl. tests): in-memory caches, zero infrastructure.</li>
 *   <li>Cache failures never fail the request: {@link CacheErrorHandler}
 *   logs and falls through to the database.</li>
 * </ul>
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    public static final String CUSTOMERS = "customers";
    public static final String FRAUD_QUEUE = "fraudQueue";

    private final InfraProperties properties;
    private final ObjectProvider<RedisConnectionFactory> redisFactory;

    public CacheConfig(InfraProperties properties, ObjectProvider<RedisConnectionFactory> redisFactory) {
        this.properties = properties;
        this.redisFactory = redisFactory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                properties.getRedis().getHost(), properties.getRedis().getPort());
        if (properties.getRedis().getPassword() != null
                && !properties.getRedis().getPassword().isBlank()) {
            standalone.setPassword(properties.getRedis().getPassword());
        }
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(properties.getRedis().getCommandTimeout())
                .shutdownTimeout(Duration.ofMillis(100))
                .build();
        return new LettuceConnectionFactory(standalone, client);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Single active cache manager: Redis-backed when a connection factory
     * exists, otherwise in-memory. Exactly one path is ever live.
     */
    @Override
    public CacheManager cacheManager() {
        RedisConnectionFactory factory = redisFactory.getIfAvailable();
        if (factory != null) {
            return RedisCacheManager.builder(factory)
                    .withCacheConfiguration(CUSTOMERS,
                            RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10)))
                    .withCacheConfiguration(FRAUD_QUEUE,
                            RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(30)))
                    .build();
        }
        return new ConcurrentMapCacheManager(CUSTOMERS, FRAUD_QUEUE);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache get failed ({}), falling through to source correlationId={}",
                        cache.getName(), CorrelationIdFilter.current(), ex);
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache put failed ({}) correlationId={}",
                        cache.getName(), CorrelationIdFilter.current(), ex);
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache evict failed ({}) correlationId={}",
                        cache.getName(), CorrelationIdFilter.current(), ex);
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Cache clear failed ({}) correlationId={}",
                        cache.getName(), CorrelationIdFilter.current(), ex);
            }
        };
    }
}
