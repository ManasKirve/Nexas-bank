package com.nexabank.fraud;

import java.util.List;

/**
 * Deterministic, explainable outcome of one fraud evaluation.
 *
 * @param riskScore bounded 0–100 total of triggered factor contributions
 * @param riskLevel band derived from the score
 * @param decision  APPROVE / REVIEW / BLOCK from configured thresholds
 * @param factors   triggered risk factors (empty when clean)
 */
public record FraudResult(int riskScore, RiskLevel riskLevel, FraudDecision decision, List<RiskFactor> factors) {
    public FraudResult {
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("Risk score must be within 0–100");
        }
        factors = factors == null ? List.of() : List.copyOf(factors);
    }
}
