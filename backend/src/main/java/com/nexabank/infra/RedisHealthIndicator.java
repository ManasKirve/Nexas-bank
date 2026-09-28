package com.nexabank.infra;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Redis health: UP when reachable, DOWN when enabled but failing, UNKNOWN
 * when Redis is disabled (no infrastructure required). Never exposes
 * credentials or connection strings.
 */
@Component("redis")
public class RedisHealthIndicator implements HealthIndicator {

    private final InfraProperties properties;
    private final org.springframework.beans.factory.ObjectProvider<
            org.springframework.data.redis.connection.RedisConnectionFactory> factory;

    public RedisHealthIndicator(
            InfraProperties properties,
            org.springframework.beans.factory.ObjectProvider<
                    org.springframework.data.redis.connection.RedisConnectionFactory> factory) {
        this.properties = properties;
        this.factory = factory;
    }

    @Override
    public Health health() {
        var connectionFactory = factory.getIfAvailable();
        if (!properties.getRedis().isEnabled() || connectionFactory == null) {
            return Health.unknown().withDetail("mode", "disabled").build();
        }
        try (var connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            return Health.up()
                    .withDetail("mode", "enabled")
                    .withDetail("ping", pong)
                    .build();
        } catch (RuntimeException ex) {
            return Health.down()
                    .withDetail("mode", "enabled")
                    .withDetail("error", "Redis unreachable")
                    .build();
        }
    }
}
