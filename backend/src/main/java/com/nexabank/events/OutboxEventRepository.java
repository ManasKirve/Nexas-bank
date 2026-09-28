package com.nexabank.events;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Outbox persistence. The publisher polls bounded oldest-first batches of
 * PENDING rows; everything else is keyed by the idempotency event id.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAscIdAsc(OutboxStatus status, Pageable pageable);

    Optional<OutboxEvent> findByEventId(String eventId);

    long countByStatus(OutboxStatus status);
}
