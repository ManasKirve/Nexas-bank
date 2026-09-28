package com.nexabank.fraud.dto;

import java.util.List;

/** Analyst dashboard summary: queue counts plus recent high-risk work. */
public record FraudDashboardResponse(
        long openAlerts,
        long underReviewAlerts,
        long resolvedAlerts,
        long falsePositiveAlerts,
        long highRiskAlerts,
        long blockedEvaluations,
        long reviewEvaluations,
        List<FraudAlertResponse> recentAlerts
) {
}
