package com.nexabank.fraud.rules;

import com.nexabank.entity.Transaction;
import com.nexabank.entity.TransactionType;
import com.nexabank.fraud.FraudEvaluationContext;
import com.nexabank.fraud.FraudProperties;
import com.nexabank.fraud.FraudRule;
import com.nexabank.fraud.RiskFactor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Rule 3 — Rapid Withdrawals. Triggers when the configured number of
 * withdrawals are already settled within the trailing window (default 3 in
 * 10 minutes).
 *
 * <p>Counting semantic: only previously <em>settled</em> ledger rows count.
 * The fraud gate runs before the ledger insert, so the current attempt has
 * no row yet; with the default threshold the 4th withdrawal inside the
 * window triggers. This keeps the rule deterministic under concurrent,
 * lock-serialized attempts. Applies to withdrawal attempts only.</p>
 */
@Component
public class RapidWithdrawalsRule implements FraudRule {

    private final FraudProperties properties;

    public RapidWithdrawalsRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "RAPID_WITHDRAWALS";
    }

    @Override
    public String description() {
        return "Multiple withdrawals occurred within the configured window";
    }

    @Override
    public int scoreContribution() {
        return properties.getRules().getRapidWithdrawalScore();
    }

    @Override
    public Optional<RiskFactor> evaluate(FraudEvaluationContext context) {
        if (context.type() != TransactionType.WITHDRAWAL) {
            return Optional.empty();
        }
        int windowMinutes = properties.getRules().getRapidWithdrawalWindowMinutes();
        int threshold = properties.getRules().getRapidWithdrawalCount();
        Instant cutoff = context.now().minusSeconds((long) windowMinutes * 60L);
        long prior = context.recentTransactions().stream()
                .filter(t -> t.getType() == TransactionType.WITHDRAWAL)
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(cutoff))
                .count();
        if (prior >= threshold) {
            return Optional.of(new RiskFactor(code(), description(), scoreContribution()));
        }
        return Optional.empty();
    }
}
