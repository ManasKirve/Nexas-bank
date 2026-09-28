package com.nexabank.events;

import com.nexabank.infra.CorrelationIdFilter;
import com.nexabank.infra.NexaBankMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Non-authoritative event consumers.
 *
 * <p>{@code fraud-alerts} → notification-style handling of
 * {@code FraudAlertCreated} (analyst attention logging + metrics).
 * {@code nexabank.transactions} → analytics handling of
 * {@code TransactionCompleted} (metrics only).</p>
 *
 * <p>Consumers NEVER mutate balances or the ledger. Every delivery passes
 * the idempotency guard first, so redeliveries are harmless. Listeners are
 * only registered when {@code app.kafka.enabled=true}.</p>
 */
@Component
public class FraudAlertEventConsumer {

    static final String FRAUD_CONSUMER = "fraud-alert-notifier";
    static final String ANALYTICS_CONSUMER = "transaction-analytics";

    private static final Logger log = LoggerFactory.getLogger(FraudAlertEventConsumer.class);

    private final EventDeduplicationService deduplication;
    private final NexaBankMetrics metrics;
    private final ObjectMapper objectMapper;

    public FraudAlertEventConsumer(
            EventDeduplicationService deduplication,
            NexaBankMetrics metrics,
            ObjectMapper objectMapper) {
        this.deduplication = deduplication;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.fraud-alert-topic:nexabank.fraud-alerts}",
            groupId = "${app.kafka.consumer-group:nexabank-analytics}",
            autoStartup = "${app.kafka.enabled:false}")
    public void onFraudAlert(String payload) {
        DomainEvents.FraudAlertCreated event = parse(payload, DomainEvents.FraudAlertCreated.class);
        if (event == null) {
            metrics.eventConsumed("FRAUD_ALERT_CREATED", "poison");
            return;
        }
        if (!deduplication.markIfFirst(event.eventId(), FRAUD_CONSUMER)) {
            metrics.eventConsumed("FRAUD_ALERT_CREATED", "duplicate");
            log.debug("Ignoring duplicate fraud-alert event {}", event.eventId());
            return;
        }
        // Notification-style handling: structured log for the analyst queue.
        // No real notification provider in this phase (deferred).
        log.info("NOTIFY fraud-alert: alertId={} evaluationId={} accountId={} severity={} score={} decision={} correlationId={}",
                event.alertId(), event.evaluationId(), event.accountId(), event.severity(),
                event.riskScore(), event.decision(), CorrelationIdFilter.current());
        metrics.eventConsumed("FRAUD_ALERT_CREATED", "processed");
    }

    @KafkaListener(
            topics = "${app.kafka.transaction-topic:nexabank.transactions}",
            groupId = "${app.kafka.consumer-group:nexabank-analytics}",
            autoStartup = "${app.kafka.enabled:false}")
    public void onTransaction(String payload) {
        DomainEvents.TransactionCompleted event = parse(payload, DomainEvents.TransactionCompleted.class);
        if (event == null) {
            metrics.eventConsumed("TRANSACTION_COMPLETED", "poison");
            return;
        }
        if (!deduplication.markIfFirst(event.eventId(), ANALYTICS_CONSUMER)) {
            metrics.eventConsumed("TRANSACTION_COMPLETED", "duplicate");
            return;
        }
        metrics.transaction("analytics-" + event.type().toLowerCase(), "observed");
        metrics.eventConsumed("TRANSACTION_COMPLETED", "processed");
    }

    private <T> T parse(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            log.warn("Discarding unparseable event payload for {} correlationId={}",
                    type.getSimpleName(), CorrelationIdFilter.current(), ex);
            return null;
        }
    }
}
