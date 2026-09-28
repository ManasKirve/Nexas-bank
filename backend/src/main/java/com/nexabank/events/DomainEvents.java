package com.nexabank.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Compact Kafka payloads. Identifiers + decision metadata only — never
 * passwords, hashes, JWTs, secrets or full customer records.
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    public record TransactionCompleted(
            String eventId,
            String transactionReference,
            Long accountId,
            String accountNumber,
            String type,
            BigDecimal amount,
            String currency,
            String status,
            Instant occurredAt
    ) {
    }

    public record TransferCompleted(
            String eventId,
            String transferReference,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            String currency,
            Instant occurredAt
    ) {
    }

    public record FraudEvaluationCompleted(
            String eventId,
            Long evaluationId,
            Long accountId,
            String attemptedReference,
            int riskScore,
            String riskLevel,
            String decision,
            String evaluationVersion,
            Instant occurredAt
    ) {
    }

    public record FraudAlertCreated(
            String eventId,
            Long alertId,
            Long evaluationId,
            Long accountId,
            String severity,
            int riskScore,
            String decision,
            Instant occurredAt
    ) {
    }
}
