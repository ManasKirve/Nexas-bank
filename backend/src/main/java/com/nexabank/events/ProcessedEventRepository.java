package com.nexabank.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Processed-event marker persistence for idempotent consumption.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    Optional<ProcessedEvent> findByEventIdAndConsumerName(String eventId, String consumerName);

    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);
}
