package com.nexabank.infra;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Application metrics (Micrometer).
 *
 * <p>Counters: {@code nexabank.transactions} (tags: type, result),
 * {@code nexabank.fraud.evaluations} (tags: decision),
 * {@code nexabank.fraud.alerts} (tags: severity),
 * {@code nexabank.events.published/consumed} (tags: event-type).
 * Timers: transaction, transfer and fraud-evaluation durations.</p>
 *
 * <p>Cardinality discipline: tags use small fixed vocabularies only
 * (operation type, decision, severity, event type). References, user ids
 * and account ids are NEVER label values.</p>
 */
@Component
public class NexaBankMetrics {

    private final MeterRegistry registry;

    public NexaBankMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void transaction(String type, String result) {
        Counter.builder("nexabank.transactions")
                .description("Completed or held money movements")
                .tag("type", type)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    public void fraudEvaluation(String decision) {
        Counter.builder("nexabank.fraud.evaluations")
                .description("Fraud evaluations by decision")
                .tag("decision", decision)
                .register(registry)
                .increment();
    }

    public void fraudAlert(String severity) {
        Counter.builder("nexabank.fraud.alerts")
                .description("Fraud alerts created by severity")
                .tag("severity", severity)
                .register(registry)
                .increment();
    }

    public void eventPublished(String eventType) {
        Counter.builder("nexabank.events.published")
                .description("Outbox events published to Kafka")
                .tag("event-type", eventType)
                .register(registry)
                .increment();
    }

    public void eventConsumed(String eventType, String outcome) {
        Counter.builder("nexabank.events.consumed")
                .description("Kafka events consumed by outcome")
                .tag("event-type", eventType)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public Sample startTimer() {
        return new Sample(System.nanoTime());
    }

    public void recordTransaction(Sample sample) {
        record("nexabank.transaction.duration", "Money movement processing duration", sample);
    }

    public void recordTransfer(Sample sample) {
        record("nexabank.transfer.duration", "Transfer processing duration", sample);
    }

    public void recordFraudEvaluation(Sample sample) {
        record("nexabank.fraud.evaluation.duration", "Fraud evaluation duration", sample);
    }

    private void record(String name, String description, Sample sample) {
        Timer.builder(name)
                .description(description)
                .register(registry)
                .record(Duration.ofNanos(System.nanoTime() - sample.startedAt()));
    }

    /** Opaque timer handle (no clock coupling for callers). */
    public record Sample(long startedAt) {
        public long elapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        }
    }
}
