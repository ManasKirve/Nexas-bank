package com.nexabank.fraud;

/**
 * Educational risk band derived from the deterministic 0–100 risk score.
 *
 * <p>Application-specific thresholds (not universal banking standards):
 * 0–29 LOW, 30–59 MEDIUM, 60–79 HIGH, 80–100 CRITICAL.</p>
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static RiskLevel fromScore(int score) {
        if (score >= 80) {
            return CRITICAL;
        }
        if (score >= 60) {
            return HIGH;
        }
        if (score >= 30) {
            return MEDIUM;
        }
        return LOW;
    }
}
