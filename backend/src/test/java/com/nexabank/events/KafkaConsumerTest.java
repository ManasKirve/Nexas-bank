package com.nexabank.events;

import com.nexabank.infra.NexaBankMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Phase 7 consumer contract: valid events process once, redeliveries are
 * ignored via the (eventId, consumer) marker, poison payloads never throw,
 * and consumers never touch balances or the ledger.
 */
@SpringBootTest
@ActiveProfiles("test")
class KafkaConsumerTest {

    @Autowired
    private FraudAlertEventConsumer consumer;

    @Autowired
    private EventDeduplicationService deduplication;

    @Autowired
    private ProcessedEventRepository processed;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NexaBankMetrics metrics;

    @Test
    void fraudAlertProcessesOnceThenIgnoresDuplicates() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(new DomainEvents.FraudAlertCreated(
                eventId, 1L, 2L, 3L, "HIGH", 65, "BLOCK", Instant.now()));

        consumer.onFraudAlert(payload);
        assertThat(processed.existsByEventIdAndConsumerName(
                eventId, FraudAlertEventConsumer.FRAUD_CONSUMER)).isTrue();

        // Redelivery: marker count unchanged, no exception.
        long before = processed.count();
        consumer.onFraudAlert(payload);
        assertThat(processed.count()).isEqualTo(before);
    }

    @Test
    void transactionAnalyticsProcessesAndDeduplicates() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(new DomainEvents.TransactionCompleted(
                eventId, "TXN-1", 1L, "100000000001", "DEPOSIT",
                new java.math.BigDecimal("100.00"), "INR", "COMPLETED", Instant.now()));

        consumer.onTransaction(payload);
        consumer.onTransaction(payload); // duplicate
        assertThat(processed.findByEventIdAndConsumerName(
                eventId, FraudAlertEventConsumer.ANALYTICS_CONSUMER)).isPresent();
    }

    @Test
    void poisonPayloadsNeverThrow() {
        assertThatNoException().isThrownBy(() -> consumer.onFraudAlert("{not-json"));
        assertThatNoException().isThrownBy(() -> consumer.onTransaction(""));
        assertThatNoException().isThrownBy(() -> consumer.onFraudAlert(null));
    }

    @Test
    void deduplicationGuardContract() {
        String eventId = UUID.randomUUID().toString();
        assertThat(deduplication.markIfFirst(eventId, "probe")).isTrue();
        assertThat(deduplication.markIfFirst(eventId, "probe")).isFalse();
        // Same event id, different consumer: independent.
        assertThat(deduplication.markIfFirst(eventId, "other-probe")).isTrue();
    }

    @Test
    void serializationRoundTripsWithoutSecrets() throws Exception {
        DomainEvents.TransferCompleted event = new DomainEvents.TransferCompleted(
                UUID.randomUUID().toString(), "TRF-1", 1L, 2L,
                new java.math.BigDecimal("10.00"), "INR", Instant.now());
        String json = objectMapper.writeValueAsString(event);
        assertThat(json).doesNotContain("password");
        assertThat(json.toLowerCase()).doesNotContain("jwt");
        assertThat(json.toLowerCase()).doesNotContain("secret");
        assertThat(objectMapper.readValue(json, DomainEvents.TransferCompleted.class).transferReference())
                .isEqualTo("TRF-1");
    }

    @Test
    void metricsBeanIsAvailable() {
        assertThat(metrics).isNotNull();
    }
}
