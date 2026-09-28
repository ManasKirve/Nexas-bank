package com.nexabank.infra;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Properties;

/**
 * Kafka health: UP when the broker answers, DOWN when enabled but
 * unreachable, UNKNOWN when Kafka is disabled. Uses a short describe call
 * with a bounded timeout — never blocks startup or requests.
 */
@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator {

    private final InfraProperties properties;

    public KafkaHealthIndicator(InfraProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.getKafka().isEnabled()) {
            return Health.unknown().withDetail("mode", "disabled").build();
        }
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                properties.getKafka().getBootstrapServers());
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);
        try (AdminClient admin = AdminClient.create(config)) {
            String clusterId = admin.describeCluster()
                    .clusterId()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            return Health.up()
                    .withDetail("mode", "enabled")
                    .withDetail("clusterId", clusterId)
                    .build();
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("mode", "enabled")
                    .withDetail("error", "Kafka unreachable")
                    .build();
        }
    }

    /** Visible for tests. */
    Duration timeout() {
        return Duration.ofSeconds(5);
    }
}
