package com.nexabank.fraud.dto;

import com.nexabank.fraud.FraudAlert;
import com.nexabank.fraud.FraudAlertSeverity;
import com.nexabank.fraud.FraudAlertStatus;
import com.nexabank.fraud.RiskFactor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Analyst-facing fraud alert projection with embedded evaluation context.
 */
public record FraudAlertResponse(
        Long id,
        FraudAlertSeverity severity,
        FraudAlertStatus status,
        String reason,
        Instant createdAt,
        Instant reviewedAt,
        String reviewedBy,
        Long accountId,
        String accountNumber,
        Long transactionId,
        Long evaluationId,
        int riskScore,
        String decision,
        String riskLevel,
        BigDecimal amount,
        String currency,
        String attemptedReference,
        String transferReference,
        String evaluationVersion,
        List<RiskFactor> factors
) {
    public static FraudAlertResponse from(FraudAlert alert, List<RiskFactor> factors) {
        var evaluation = alert.getFraudEvaluation();
        return new FraudAlertResponse(
                alert.getId(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getReason(),
                alert.getCreatedAt(),
                alert.getReviewedAt(),
                alert.getReviewedBy(),
                alert.getAccount().getId(),
                alert.getAccount().getAccountNumber(),
                alert.getTransaction() == null ? null : alert.getTransaction().getId(),
                evaluation.getId(),
                evaluation.getRiskScore(),
                evaluation.getDecision().name(),
                evaluation.getRiskLevel().name(),
                evaluation.getAmount(),
                evaluation.getCurrency(),
                evaluation.getAttemptedReference(),
                evaluation.getTransferReference(),
                evaluation.getEvaluationVersion(),
                factors);
    }
}
