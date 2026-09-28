package com.nexabank;

import com.nexabank.fraud.FraudProperties;
import com.nexabank.infra.InfraProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * NexaBank backend entry point.
 *
 * <p>Phase 1: modular-monolith foundation only. Future modules
 * (auth, accounts, transactions, fraud, audit, notifications)
 * will live in their own packages behind Controller -&gt; Service -&gt; Repository layers.</p>
 */
@SpringBootApplication(exclude = {
        // The Redis client is owned by Phase 7 conditional config
        // (CacheConfig): it exists only when app.redis.enabled is true,
        // so tests and minimal runs need no Redis server.
        DataRedisAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class
})
@EnableConfigurationProperties({FraudProperties.class, InfraProperties.class})
@EnableCaching
@EnableScheduling
public class NexaBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexaBankApplication.class, args);
    }
}
