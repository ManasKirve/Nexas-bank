package com.nexabank.fraud.rules;

import com.nexabank.entity.Transaction;
import com.nexabank.fraud.FraudEvaluationContext;
import com.nexabank.fraud.FraudProperties;
import com.nexabank.fraud.FraudRule;
import com.nexabank.fraud.RiskFactor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Rule 6 — Activity Spike. Deterministic baseline comparison (no ML):
 * triggers when the attempted amount exceeds {@code multiplier × recent
 * average} over the bounded lookback, requiring a minimum history size so
 * single-transaction accounts never spike.
 */
@Component
public class ActivitySpikeRule implements FraudRule {

    private final FraudProperties properties;

    public ActivitySpikeRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "ACCOUNT_ACTIVITY_SPIKE";
    }

    @Override
    public String description() {
        return "Transaction amount is significantly higher than the recent average";
    }

    @Override
    public int scoreContribution() {
        return properties.getRules().getActivitySpikeScore();
    }

    @Override
    public Optional<RiskFactor> evaluate(FraudEvaluationContext context) {
        int minHistory = properties.getRules().getActivitySpikeMinHistory();
        int lookback = properties.getRules().getActivitySpikeLookback();
        double multiplier = properties.getRules().getActivitySpikeMultiplier();

        List<Transaction> baseline = context.recentTransactions().stream()
                .limit(Math.max(1, lookback))
                .toList();
        if (baseline.size() < minHistory) {
            return Optional.empty();
        }
        BigDecimal sum = baseline.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(
                BigDecimal.valueOf(baseline.size()), 2, RoundingMode.HALF_UP);
        if (average.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal threshold = average.multiply(BigDecimal.valueOf(multiplier));
        if (context.amount().compareTo(threshold) > 0) {
            return Optional.of(new RiskFactor(code(), description(), scoreContribution()));
        }
        return Optional.empty();
    }
}
