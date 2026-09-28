package com.nexabank.fraud;

import java.util.Optional;

/**
 * Strategy contract for one modular fraud rule.
 *
 * <p>Rules are pure and side-effect free: they read the pre-loaded
 * {@link FraudEvaluationContext} and return a {@link RiskFactor} when they
 * trigger, or empty when they do not. Adding a rule means adding one class
 * that implements this interface — never editing a giant method.</p>
 */
public interface FraudRule {

    /** Stable rule code, also used as the risk-factor code. */
    String code();

    /** Analyst-facing description of what this rule detects. */
    String description();

    /** Deterministic score contribution when this rule triggers. */
    int scoreContribution();

    /**
     * Evaluates this rule against the pre-loaded context.
     *
     * @return the triggered risk factor, or empty when not triggered
     */
    Optional<RiskFactor> evaluate(FraudEvaluationContext context);
}
