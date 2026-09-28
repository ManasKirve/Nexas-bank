package com.nexabank.fraud.dto;

import com.nexabank.entity.TransactionType;
import com.nexabank.fraud.FraudDecision;
import com.nexabank.fraud.FraudEvaluation;
import com.nexabank.fraud.RiskFactor;
import com.nexabank.fraud.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Explainable fraud evaluation projection for analysts. Never exposed to
 * customers — only FRAUD_ANALYST and ADMIN.
 */
public record FraudEvaluationResponse(
        Long id,
        Long accountId,
        String accountNumber,
        Long transactionId,
        String attemptedReference,
        String transferReference,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        int riskScore,
        RiskLevel riskLevel,
        FraudDecision decision,
        List<RiskFactor> factors,
        String evaluationVersion,
        String evaluationReason,
        String evaluatedBy,
        Instant evaluatedAt
) {
    public static FraudEvaluationResponse from(FraudEvaluation evaluation, List<RiskFactor> factors) {
        return new FraudEvaluationResponse(
                evaluation.getId(),
                evaluation.getAccount().getId(),
                evaluation.getAccount().getAccountNumber(),
                evaluation.getTransaction() == null ? null : evaluation.getTransaction().getId(),
                evaluation.getAttemptedReference(),
                evaluation.getTransferReference(),
                evaluation.getTransactionType(),
                evaluation.getAmount(),
                evaluation.getCurrency(),
                evaluation.getRiskScore(),
                evaluation.getRiskLevel(),
                evaluation.getDecision(),
                factors,
                evaluation.getEvaluationVersion(),
                evaluation.getEvaluationReason(),
                evaluation.getEvaluatedBy(),
                evaluation.getEvaluatedAt());
    }
}
