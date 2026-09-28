package com.nexabank.fraud.rules;

import com.nexabank.entity.TransactionType;
import com.nexabank.fraud.FraudEvaluationContext;
import com.nexabank.fraud.FraudProperties;
import com.nexabank.fraud.FraudRule;
import com.nexabank.fraud.RiskFactor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Rule 4 — Rapid Transfers. Triggers when the configured number of outgoing
 * transfers (debit legs) are already settled within the trailing window
 * (default 3 in 10 minutes).
 *
 * <p>Counting semantic: only previously <em>settled outgoing</em> (debit)
 * legs count. Inbound credit legs are not actor-initiated risk and never
 * count; the current attempt has no ledger row yet because the fraud gate
 * runs before persistence, so with the default threshold the 4th transfer
 * inside the window triggers. Applies to transfer attempts only.</p>
 */
@Component
public class RapidTransfersRule implements FraudRule {

    private final FraudProperties properties;

    public RapidTransfersRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "RAPID_TRANSFERS";
    }

    @Override
    public String description() {
        return "Multiple transfers occurred within the configured window";
    }

    @Override
    public int scoreContribution() {
        return properties.getRules().getRapidTransferScore();
    }

    @Override
    public Optional<RiskFactor> evaluate(FraudEvaluationContext context) {
        if (context.type() != TransactionType.TRANSFER_DEBIT) {
            return Optional.empty();
        }
        int windowMinutes = properties.getRules().getRapidTransferWindowMinutes();
        int threshold = properties.getRules().getRapidTransferCount();
        Instant cutoff = context.now().minusSeconds((long) windowMinutes * 60L);
        long prior = context.recentTransactions().stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER_DEBIT)
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(cutoff))
                .count();
        if (prior >= threshold) {
            return Optional.of(new RiskFactor(code(), description(), scoreContribution()));
        }
        return Optional.empty();
    }
}
