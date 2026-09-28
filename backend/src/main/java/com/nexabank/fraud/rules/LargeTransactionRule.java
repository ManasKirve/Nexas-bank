package com.nexabank.fraud.rules;

import com.nexabank.fraud.FraudEvaluationContext;
import com.nexabank.fraud.FraudProperties;
import com.nexabank.fraud.FraudRule;
import com.nexabank.fraud.RiskFactor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 1 — Large Transaction. Triggers when the attempted amount exceeds the
 * configured threshold (educational default ₹50,000).
 */
@Component
public class LargeTransactionRule implements FraudRule {

    private final FraudProperties properties;

    public LargeTransactionRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "LARGE_TRANSACTION";
    }

    @Override
    public String description() {
        return "Transaction exceeded configured threshold";
    }

    @Override
    public int scoreContribution() {
        return properties.getRules().getLargeTransactionScore();
    }

    @Override
    public Optional<RiskFactor> evaluate(FraudEvaluationContext context) {
        if (context.amount().compareTo(properties.getRules().getLargeTransactionThreshold()) > 0) {
            return Optional.of(new RiskFactor(code(), description(), scoreContribution()));
        }
        return Optional.empty();
    }
}
