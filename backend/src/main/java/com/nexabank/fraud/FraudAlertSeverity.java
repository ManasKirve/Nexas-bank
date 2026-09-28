package com.nexabank.fraud;

/**
 * Alert severity shown to fraud analysts. Mirrors the evaluation risk band
 * so the queue can be triaged without exposing internal rule details to
 * customers.
 */
public enum FraudAlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static FraudAlertSeverity fromRiskLevel(RiskLevel level) {
        return switch (level) {
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
            case CRITICAL -> CRITICAL;
        };
    }
}
