package com.nexabank.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Consumer-side processed-event marker for idempotent Kafka consumption.
 *
 * <p>Kafka delivers at-least-once: redeliveries, rebalances and publisher
 * retries can all resend an event. The unique constraint on
 * {@code (event_id, consumer_name)} makes "check then insert" safe — the
 * second insert fails and the duplicate is ignored.</p>
 */
@Entity
@Table(
        name = "processed_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_processed_events_event_consumer",
                        columnNames = {"event_id", "consumer_name"})
        },
        indexes = {
                @Index(name = "idx_processed_events_consumer", columnList = "consumer_name")
        }
)
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "consumer_name", nullable = false, length = 64)
    private String consumerName;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String consumerName) {
        this.eventId = eventId;
        this.consumerName = consumerName;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
