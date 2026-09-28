package com.nexabank.events;

/**
 * Outbox event types. Payloads carry identifiers and decision metadata only —
 * never passwords, JWTs, secrets or full customer records.
 */
public enum OutboxEventType {
    TRANSACTION_COMPLETED,
    TRANSFER_COMPLETED,
    FRAUD_EVALUATION_COMPLETED,
    FRAUD_ALERT_CREATED
}
