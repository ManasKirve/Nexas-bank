package com.nexabank.entity;

/**
 * Lifecycle status of a financial {@link Transaction}.
 *
 * <p>Phase 4 flows: {@code PENDING → COMPLETED} on success,
 * {@code PENDING → FAILED} when validation or business processing rejects the
 * operation. {@code REVERSED} exists for future compensating-transaction
 * support — there is no reversal workflow in this phase, and completed
 * transactions are never mutated in place.</p>
 */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REVERSED
}
