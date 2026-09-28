package com.nexabank.fraud;

/**
 * Analyst review lifecycle for a fraud alert.
 *
 * <p>Allowed transitions (server-side validated):</p>
 * <pre>
 * OPEN → UNDER_REVIEW → RESOLVED
 * OPEN → UNDER_REVIEW → FALSE_POSITIVE
 * </pre>
 * <p>Direct OPEN → RESOLVED / FALSE_POSITIVE is rejected so every alert
 * records an analyst taking ownership first. Terminal states (RESOLVED,
 * FALSE_POSITIVE) accept no further transitions.</p>
 */
public enum FraudAlertStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    FALSE_POSITIVE
}
