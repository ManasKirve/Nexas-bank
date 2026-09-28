package com.nexabank.events;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side idempotency guard. {@link #markIfFirst} returns true the
 * first time an event id is seen for a consumer and false for every
 * redelivery — the unique constraint is the backstop against races.
 */
@Service
public class EventDeduplicationService {

    private final ProcessedEventRepository processed;

    public EventDeduplicationService(ProcessedEventRepository processed) {
        this.processed = processed;
    }

    /**
     * @return true when this is the first delivery (marker recorded),
     *         false when the event was already processed
     */
    @Transactional
    public boolean markIfFirst(String eventId, String consumerName) {
        if (processed.existsByEventIdAndConsumerName(eventId, consumerName)) {
            return false;
        }
        try {
            processed.saveAndFlush(new ProcessedEvent(eventId, consumerName));
            return true;
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent delivery of the same event.
            return false;
        }
    }
}
