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
 * Rule 5 — New Beneficiary. Triggers when a transfer targets a beneficiary
 * created within the configured window (default 24 hours).
 */
@Component
public class NewBeneficiaryRule implements FraudRule {

    private final FraudProperties properties;

    public NewBeneficiaryRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "NEW_BENEFICIARY";
    }

    @Override
    public String description() {
        return "Beneficiary was recently created";
    }

    @Override
    public int scoreContribution() {
        return properties.getRules().getNewBeneficiaryScore();
    }

    @Override
    public Optional<RiskFactor> evaluate(FraudEvaluationContext context) {
        if (context.type() != TransactionType.TRANSFER_DEBIT) {
            return Optional.empty();
        }
        if (context.beneficiaryCreatedAt() == null) {
            return Optional.empty();
        }
        int windowHours = properties.getRules().getNewBeneficiaryWindowHours();
        Instant cutoff = context.now().minusSeconds((long) windowHours * 3600L);
        if (context.beneficiaryCreatedAt().isAfter(cutoff)) {
            return Optional.of(new RiskFactor(code(), description(), scoreContribution()));
        }
        return Optional.empty();
    }
}
