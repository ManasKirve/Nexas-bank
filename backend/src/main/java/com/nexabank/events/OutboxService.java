package com.nexabank.events;

import com.nexabank.infra.InfraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Transactional outbox writer.
 *
 * <p>Callers invoke {@link #stage} inside their business transaction
 * (REQUIRED propagation joins it): the event row commits if and only if
 * the business state commits, and rolls back with it. Nothing is ever
 * sent to Kafka from inside a business transaction.</p>
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outbox;
    private final InfraProperties properties;
    private final ObjectMapper objectMapper;

    public OutboxService(
            OutboxEventRepository outbox, InfraProperties properties, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Stages one event in the current transaction.
     *
     * @return the staged event (with its idempotency event id)
     */
    @Transactional
    public OutboxEvent stage(
            OutboxEventType eventType, String aggregateType, String aggregateId,
            Object payload, String topic) {
        String json = serialize(payload);
        OutboxEvent event = new OutboxEvent(eventType, aggregateType, aggregateId, json, topic);
        OutboxEvent saved = outbox.save(event);
        log.debug("Staged outbox event {} {} for {}:{}",
                saved.getEventId(), eventType, aggregateType, aggregateId);
        return saved;
    }

    public String transactionTopic() {
        return properties.getKafka().getTransactionTopic();
    }

    public String transferTopic() {
        return properties.getKafka().getTransferTopic();
    }

    public String fraudEvaluationTopic() {
        return properties.getKafka().getFraudEvaluationTopic();
    }

    public String fraudAlertTopic() {
        return properties.getKafka().getFraudAlertTopic();
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Outbox payload is not serializable", ex);
        }
    }
}
