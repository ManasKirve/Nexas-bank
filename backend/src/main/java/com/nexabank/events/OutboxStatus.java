package com.nexabank.events;

/** Outbox delivery status. PENDING rows are picked up by the publisher. */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
