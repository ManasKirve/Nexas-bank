package com.nexabank.events;

import com.nexabank.infra.CorrelationIdFilter;
import com.nexabank.infra.InfraProperties;
import com.nexabank.infra.NexaBankMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled outbox publisher (every {@code app.outbox.poll-delay}, default
 * 5s).
 *
 * <ul>
 *   <li>Reads a bounded oldest-first batch of PENDING rows — never the
 *   whole table, never blocking money-movement requests.</li>
 *   <li>Each event publishes in its own isolated unit (self-proxy,
 *   REQUIRES_NEW): one poison event cannot roll back its siblings.</li>
 *   <li>Success → PUBLISHED + timestamp. Failure → retryCount++ with the
 *   (truncated, non-secret) error; past {@code max-retries} → FAILED so a
 *   dead event stops consuming retries but stays inspectable.</li>
 *   <li>Kafka down/disabled → everything stays PENDING and is retried
 *   later. Financial state is untouched either way.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "publisher-enabled",
        havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outbox;
    private final KafkaEventPublisher sender;
    private final InfraProperties properties;
    private final NexaBankMetrics metrics;
    private final ObjectProvider<OutboxPublisher> self;

    public OutboxPublisher(
            OutboxEventRepository outbox,
            KafkaEventPublisher sender,
            InfraProperties properties,
            NexaBankMetrics metrics,
            ObjectProvider<OutboxPublisher> self) {
        this.outbox = outbox;
        this.sender = sender;
        this.properties = properties;
        this.metrics = metrics;
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay:5s}")
    public void publishPending() {
        int batchSize = Math.max(1, Math.min(500, properties.getOutbox().getBatchSize()));
        List<OutboxEvent> batch = outbox.findByStatusOrderByCreatedAtAscIdAsc(
                OutboxStatus.PENDING, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Outbox publisher picked up {} pending event(s)", batch.size());
        for (OutboxEvent event : batch) {
            try {
                self.getObject().publishOne(event.getId());
            } catch (RuntimeException ex) {
                log.warn("Outbox event {} failed in isolation correlationId={}",
                        event.getEventId(), CorrelationIdFilter.current(), ex);
            }
        }
    }

    /**
     * Publishes one event in isolation. Success and failure bookkeeping
     * commit even when the send itself blew up.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(Long id) {
        OutboxEvent event = outbox.findById(id).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            return;
        }
        try {
            sender.send(event.getTopic(), event.getAggregateId(), event.getPayload());
        } catch (RuntimeException ex) {
            int retries = event.getRetryCount() + 1;
            event.setRetryCount(retries);
            event.setLastError(truncated(ex.getMessage()));
            if (retries >= Math.max(1, properties.getOutbox().getMaxRetries())) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Outbox event {} marked FAILED after {} attempts correlationId={}",
                        event.getEventId(), retries, CorrelationIdFilter.current(), ex);
            } else {
                log.warn("Outbox event {} publish failed (attempt {}) — will retry correlationId={}",
                        event.getEventId(), retries, CorrelationIdFilter.current(), ex);
            }
            outbox.save(event);
            return;
        }
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        event.setLastError(null);
        outbox.save(event);
        metrics.eventPublished(event.getEventType().name());
    }

    private String truncated(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
