package com.nexabank.fraud;

/**
 * Fraud decision for a transaction evaluation.
 *
 * <p>Educational, application-specific meanings:</p>
 * <ul>
 *   <li>APPROVE — transaction may proceed normally.</li>
 *   <li>REVIEW — transaction is held (no balance mutation); a fraud alert is
 *   created and an analyst must review before any money moves.</li>
 *   <li>BLOCK — transaction must not complete; no balance mutation and a
 *   fraud alert is created.</li>
 * </ul>
 *
 * <p>The engine never mutates balances itself; it only advises the
 * transaction/transfer services at the correct service boundary.</p>
 */
public enum FraudDecision {
    APPROVE,
    REVIEW,
    BLOCK
}
