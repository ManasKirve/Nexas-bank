package com.nexabank.fraud.rules;

import com.nexabank.entity.Transaction;
import com.nexabank.fraud.FraudEvaluationContext;
import com.nexabank.fraud.FraudProperties;
import com.nexabank.fraud.FraudRule;
import com.nexabank.fraud.RiskFactor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Rule 2 — High Transaction Frequency. Triggers when the account performed
 * more than the configured number of transactions inside the trailing
 * window (default: more than 5 in the previous 10 minutes).
 */
@Component
public class HighFrequencyRule implements FraudRule {

    private final FraudProperties properties;

    public HighFrequencyRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "HIGH_TRANSACTION_FREQUENCY";
    }

    @Override
    public String description() {
        return "Unusually high number of transactions in a short window";
    }

    @Override
    public int scoreContribution() {
        return properties.getRules().getHighFrequencyScore();
    }

    @Override
    public Optional<RiskFactor> evaluate(FraudEvaluationContext context) {
        int windowMinutes = properties.getRules().getHighFrequencyWindowMinutes();
        int threshold = properties.getRules().getHighFrequencyCount();
        Instant cutoff = context.now().minusSeconds((long) windowMinutes * 60L);
        long recent = context.recentTransactions().stream()
                .filter(t -> isAfter(t, cutoff))
                .count();
        if (recent > threshold) {
            return Optional.of(new RiskFactor(code(), description(), scoreContribution()));
        }
        return Optional.empty();
    }

    private boolean isAfter(Transaction transaction, Instant cutoff) {
        return transaction.getCreatedAt() != null && transaction.getCreatedAt().isAfter(cutoff);
    }
}
