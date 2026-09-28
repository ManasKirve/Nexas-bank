package com.nexabank.fraud;

/**
 * One explainable trigger produced by a fraud rule.
 *
 * @param code              stable machine-readable code (e.g. LARGE_TRANSACTION)
 * @param description       analyst-facing explanation (no secrets)
 * @param scoreContribution deterministic score contribution (0–100)
 */
public record RiskFactor(String code, String description, int scoreContribution) {
    public RiskFactor {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Risk factor code is required");
        }
        if (scoreContribution < 0) {
            throw new IllegalArgumentException("Score contribution must not be negative");
        }
    }
}
