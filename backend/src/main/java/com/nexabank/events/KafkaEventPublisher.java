package com.nexabank.events;

import com.nexabank.exception.InfraUnavailableException;
import com.nexabank.infra.InfraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Thin Kafka send boundary. Used ONLY by the outbox publisher (never from
 * inside a business transaction). When Kafka is disabled or the broker is
 * unreachable the send throws and the outbox row stays PENDING for retry —
 * the financial commit is never at stake.
 */
@Component
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final InfraProperties properties;
    private final org.springframework.beans.factory.ObjectProvider<KafkaTemplate<String, String>> templates;

    public KafkaEventPublisher(
            InfraProperties properties,
            org.springframework.beans.factory.ObjectProvider<KafkaTemplate<String, String>> templates) {
        this.properties = properties;
        this.templates = templates;
    }

    /**
     * Sends one record, blocking up to the configured delivery timeout.
     *
     * @param topic   destination topic
     * @param key     partitioning key (aggregate id — keeps one aggregate ordered)
     * @param payload serialized event JSON
     * @throws InfraUnavailableException when disabled or the send fails
     */
    public void send(String topic, String key, String payload) {
        if (!properties.getKafka().isEnabled()) {
            throw new InfraUnavailableException("Kafka is disabled (app.kafka.enabled=false)");
        }
        KafkaTemplate<String, String> template = templates.getIfAvailable();
        if (template == null) {
            throw new InfraUnavailableException("No KafkaTemplate available");
        }
        try {
            template.send(topic, key, payload)
                    .get(properties.getKafka().getDeliveryTimeout().toMillis(),
                            TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InfraUnavailableException("Kafka send interrupted for topic " + topic, ex);
        } catch (Exception ex) {
            throw new InfraUnavailableException("Kafka send failed for topic " + topic, ex);
        }
        log.debug("Published event to topic {} key {}", topic, key);
    }
}
