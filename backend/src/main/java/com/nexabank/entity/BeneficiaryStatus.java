package com.nexabank.entity;

/**
 * Lifecycle status of a {@link Beneficiary}. Deletion is a soft disable —
 * the historical financial relationship is kept for the audit trail.
 */
public enum BeneficiaryStatus {
    ACTIVE,
    DISABLED
}
