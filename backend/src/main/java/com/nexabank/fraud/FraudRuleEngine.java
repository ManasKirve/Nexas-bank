package com.nexabank.fraud;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic fraud rule engine.
 *
 * <p>Executes every registered {@link FraudRule} against the pre-loaded
 * context, sums triggered contributions (bounded 0–100) and maps the total
 * to a {@link FraudDecision} via the configured thresholds. Rules stay
 * modular — this class never contains rule logic itself.</p>
 */
@Service
public class FraudRuleEngine {

    private final List<FraudRule> rules;
    private final FraudProperties properties;

    public FraudRuleEngine(List<FraudRule> rules, FraudProperties properties) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.properties = properties;
    }

    public FraudResult evaluate(FraudEvaluationContext context) {
        List<RiskFactor> factors = new ArrayList<>();
        for (FraudRule rule : rules) {
            rule.evaluate(context).ifPresent(factors::add);
        }
        int total = factors.stream().mapToInt(RiskFactor::scoreContribution).sum();
        int bounded = Math.max(0, Math.min(100, total));
        RiskLevel level = RiskLevel.fromScore(bounded);
        FraudDecision decision = decide(bounded);
        return new FraudResult(bounded, level, decision, factors);
    }

    private FraudDecision decide(int score) {
        if (score >= properties.getBlockThreshold()) {
            return FraudDecision.BLOCK;
        }
        if (score >= properties.getReviewThreshold()) {
            return FraudDecision.REVIEW;
        }
        return FraudDecision.APPROVE;
    }
}
