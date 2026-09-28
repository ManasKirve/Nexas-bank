package com.nexabank.fraud;

import com.nexabank.entity.Account;
import com.nexabank.entity.AccountType;
import com.nexabank.entity.Customer;
import com.nexabank.entity.Transaction;
import com.nexabank.entity.TransactionType;
import com.nexabank.fraud.rules.ActivitySpikeRule;
import com.nexabank.fraud.rules.HighFrequencyRule;
import com.nexabank.fraud.rules.LargeTransactionRule;
import com.nexabank.fraud.rules.NewBeneficiaryRule;
import com.nexabank.fraud.rules.RapidTransfersRule;
import com.nexabank.fraud.rules.RapidWithdrawalsRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6 rule/score/decision unit tests. Pure engine tests with hand-built
 * contexts — no Spring, no database.
 */
class FraudRuleEngineTest {

    private FraudProperties properties;
    private FraudRuleEngine engine;

    @BeforeEach
    void setUp() {
        properties = new FraudProperties();
        List<FraudRule> rules = List.of(
                new LargeTransactionRule(properties),
                new HighFrequencyRule(properties),
                new RapidWithdrawalsRule(properties),
                new RapidTransfersRule(properties),
                new NewBeneficiaryRule(properties),
                new ActivitySpikeRule(properties));
        engine = new FraudRuleEngine(rules, properties);
    }

    // ---------- Rule 1: large transaction ----------

    @Test
    void largeTransactionTriggersAboveThreshold() {
        FraudEvaluationContext context = context(
                new BigDecimal("60000"), TransactionType.DEPOSIT, List.of(), null);
        Optional<RiskFactor> factor = new LargeTransactionRule(properties).evaluate(context);
        assertThat(factor).isPresent();
        assertThat(factor.get().code()).isEqualTo("LARGE_TRANSACTION");
        assertThat(factor.get().scoreContribution()).isEqualTo(25);
    }

    @Test
    void largeTransactionDoesNotTriggerAtOrBelowThreshold() {
        assertThat(new LargeTransactionRule(properties)
                .evaluate(context(new BigDecimal("50000"), TransactionType.DEPOSIT, List.of(), null)))
                .isEmpty();
        assertThat(new LargeTransactionRule(properties)
                .evaluate(context(new BigDecimal("100"), TransactionType.DEPOSIT, List.of(), null)))
                .isEmpty();
    }

    // ---------- Rule 2: high frequency ----------

    @Test
    void highFrequencyTriggersAboveCount() {
        List<Transaction> recent = stubs(6, TransactionType.DEPOSIT, "10.00", Instant.now().minusSeconds(60));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("10.00"), TransactionType.DEPOSIT, recent, null));
        assertThat(codes(result)).contains("HIGH_TRANSACTION_FREQUENCY");
    }

    @Test
    void highFrequencyDoesNotTriggerAtThreshold() {
        List<Transaction> recent = stubs(5, TransactionType.DEPOSIT, "10.00", Instant.now().minusSeconds(60));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("10.00"), TransactionType.DEPOSIT, recent, null));
        assertThat(codes(result)).doesNotContain("HIGH_TRANSACTION_FREQUENCY");
    }

    @Test
    void highFrequencyIgnoresTransactionsOutsideWindow() {
        List<Transaction> recent = stubs(10, TransactionType.DEPOSIT, "10.00",
                Instant.now().minusSeconds(3600));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("10.00"), TransactionType.DEPOSIT, recent, null));
        assertThat(codes(result)).doesNotContain("HIGH_TRANSACTION_FREQUENCY");
    }

    // ---------- Rule 3: rapid withdrawals ----------

    @Test
    void rapidWithdrawalsTriggerOnSettledHistory() {
        List<Transaction> recent = stubs(3, TransactionType.WITHDRAWAL, "10.00",
                Instant.now().minusSeconds(60));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("10.00"), TransactionType.WITHDRAWAL, recent, null));
        assertThat(codes(result)).contains("RAPID_WITHDRAWALS");
    }

    @Test
    void rapidWithdrawalsDoNotTriggerBelowThreshold() {
        List<Transaction> recent = stubs(2, TransactionType.WITHDRAWAL, "10.00",
                Instant.now().minusSeconds(60));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("10.00"), TransactionType.WITHDRAWAL, recent, null));
        assertThat(codes(result)).doesNotContain("RAPID_WITHDRAWALS");
    }

    @Test
    void rapidWithdrawalsIgnoreDeposits() {
        List<Transaction> recent = stubs(5, TransactionType.DEPOSIT, "10.00",
                Instant.now().minusSeconds(60));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("10.00"), TransactionType.WITHDRAWAL, recent, null));
        assertThat(codes(result)).doesNotContain("RAPID_WITHDRAWALS");
    }

    // ---------- Rule 4: rapid transfers ----------

    @Test
    void rapidTransfersTriggerOnSettledOutgoingHistory() {
        List<Transaction> recent = stubs(3, TransactionType.TRANSFER_DEBIT, "10.00",
                Instant.now().minusSeconds(60));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("10.00"), TransactionType.TRANSFER_DEBIT, recent, Instant.now()));
        assertThat(codes(result)).contains("RAPID_TRANSFERS");
    }

    @Test
    void rapidTransfersDoNotTriggerBelowThreshold() {
        List<Transaction> recent = stubs(2, TransactionType.TRANSFER_DEBIT, "10.00",
                Instant.now().minusSeconds(60));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("10.00"), TransactionType.TRANSFER_DEBIT, recent, Instant.now()));
        assertThat(codes(result)).doesNotContain("RAPID_TRANSFERS");
    }

    @Test
    void rapidTransfersIgnoreInboundCredits() {
        List<Transaction> recent = stubs(5, TransactionType.TRANSFER_CREDIT, "10.00",
                Instant.now().minusSeconds(60));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("10.00"), TransactionType.TRANSFER_DEBIT, recent, Instant.now()));
        assertThat(codes(result)).doesNotContain("RAPID_TRANSFERS");
    }

    @Test
    void rapidTransfersDoNotTriggerForDeposits() {
        List<Transaction> recent = stubs(5, TransactionType.TRANSFER_DEBIT, "10.00",
                Instant.now().minusSeconds(60));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("10.00"), TransactionType.DEPOSIT, recent, null));
        assertThat(codes(result)).doesNotContain("RAPID_TRANSFERS");
    }

    // ---------- Rule 5: new beneficiary ----------

    @Test
    void newBeneficiaryTriggersWithin24Hours() {
        FraudEvaluationContext context = context(
                new BigDecimal("100"), TransactionType.TRANSFER_DEBIT, List.of(),
                Instant.now().minusSeconds(3600));
        assertThat(new NewBeneficiaryRule(properties).evaluate(context)).isPresent();
    }

    @Test
    void newBeneficiaryDoesNotTriggerForOldBeneficiary() {
        FraudEvaluationContext context = context(
                new BigDecimal("100"), TransactionType.TRANSFER_DEBIT, List.of(),
                Instant.now().minusSeconds(48 * 3600L));
        assertThat(new NewBeneficiaryRule(properties).evaluate(context)).isEmpty();
    }

    @Test
    void newBeneficiaryDoesNotTriggerForNonTransfers() {
        FraudEvaluationContext context = context(
                new BigDecimal("100"), TransactionType.DEPOSIT, List.of(), Instant.now());
        assertThat(new NewBeneficiaryRule(properties).evaluate(context)).isEmpty();
    }

    // ---------- Rule 6: activity spike ----------

    @Test
    void activitySpikeTriggersAboveTwiceAverage() {
        List<Transaction> recent = stubs(3, TransactionType.DEPOSIT, "100.00",
                Instant.now().minusSeconds(3600));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("500.00"), TransactionType.DEPOSIT, recent, null));
        assertThat(codes(result)).contains("ACCOUNT_ACTIVITY_SPIKE");
    }

    @Test
    void activitySpikeRequiresMinimumHistory() {
        List<Transaction> recent = stubs(2, TransactionType.DEPOSIT, "100.00",
                Instant.now().minusSeconds(3600));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("5000.00"), TransactionType.DEPOSIT, recent, null));
        assertThat(codes(result)).doesNotContain("ACCOUNT_ACTIVITY_SPIKE");
    }

    @Test
    void activitySpikeDoesNotTriggerNearAverage() {
        List<Transaction> recent = stubs(4, TransactionType.DEPOSIT, "100.00",
                Instant.now().minusSeconds(3600));
        FraudResult result = engine.evaluate(
                context(new BigDecimal("150.00"), TransactionType.DEPOSIT, recent, null));
        assertThat(codes(result)).doesNotContain("ACCOUNT_ACTIVITY_SPIKE");
    }

    // ---------- score tests ----------

    @Test
    void noFactorsGivesZeroScore() {
        FraudResult result = engine.evaluate(
                context(new BigDecimal("100"), TransactionType.DEPOSIT, List.of(), null));
        assertThat(result.riskScore()).isEqualTo(0);
        assertThat(result.factors()).isEmpty();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void multipleFactorsSumCorrectly() {
        // Large (25) + new beneficiary (15) = 40.
        FraudEvaluationContext context = context(
                new BigDecimal("60000"), TransactionType.TRANSFER_DEBIT, List.of(),
                Instant.now());
        FraudResult result = engine.evaluate(context);
        assertThat(result.riskScore()).isEqualTo(40);
        assertThat(codes(result)).containsExactlyInAnyOrder("LARGE_TRANSACTION", "NEW_BENEFICIARY");
    }

    @Test
    void scoreIsCappedAt100() {
        FraudProperties generous = new FraudProperties();
        generous.getRules().setLargeTransactionScore(60);
        generous.getRules().setHighFrequencyScore(60);
        generous.getRules().setActivitySpikeScore(60);
        FraudRuleEngine generousEngine = new FraudRuleEngine(List.of(
                new LargeTransactionRule(generous),
                new HighFrequencyRule(generous),
                new ActivitySpikeRule(generous)), generous);
        List<Transaction> recent = stubs(6, TransactionType.DEPOSIT, "100.00",
                Instant.now().minusSeconds(60));
        // Also spike: 60000 >> avg 100.
        FraudResult result = generousEngine.evaluate(
                new FraudEvaluationContext(1L, "100000000001", new BigDecimal("60000"),
                        TransactionType.DEPOSIT, null, null, recent, Instant.now()));
        assertThat(result.riskScore()).isEqualTo(100);
    }

    // ---------- decision tests ----------

    @Test
    void belowReviewThresholdApproves() {
        FraudResult result = engine.evaluate(
                context(new BigDecimal("100"), TransactionType.DEPOSIT, List.of(), null));
        assertThat(result.decision()).isEqualTo(FraudDecision.APPROVE);
    }

    @Test
    void reviewRangeRequiresReview() {
        // 40 → REVIEW (large + new beneficiary).
        FraudEvaluationContext context = context(
                new BigDecimal("60000"), TransactionType.TRANSFER_DEBIT, List.of(), Instant.now());
        assertThat(engine.evaluate(context).decision()).isEqualTo(FraudDecision.REVIEW);
    }

    @Test
    void blockThresholdBlocks() {
        // 25 + 20 + 15 + 20 = 80 → BLOCK.
        List<Transaction> recent = new ArrayList<>();
        recent.addAll(stubs(6, TransactionType.DEPOSIT, "100.00", Instant.now().minusSeconds(60)));
        FraudEvaluationContext context = context(
                new BigDecimal("60000"), TransactionType.TRANSFER_DEBIT, recent, Instant.now());
        FraudResult result = engine.evaluate(context);
        assertThat(result.riskScore()).isGreaterThanOrEqualTo(60);
        assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
    }

    @Test
    void riskLevelsMapCorrectly() {
        assertThat(RiskLevel.fromScore(0)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(29)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(30)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromScore(59)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromScore(60)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.fromScore(79)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.fromScore(80)).isEqualTo(RiskLevel.CRITICAL);
        assertThat(RiskLevel.fromScore(100)).isEqualTo(RiskLevel.CRITICAL);
    }

    // ---------- helpers ----------

    private FraudEvaluationContext context(
            BigDecimal amount, TransactionType type, List<Transaction> recent, Instant beneficiaryCreatedAt) {
        return new FraudEvaluationContext(
                1L, "100000000001", amount, type, null, beneficiaryCreatedAt, recent, Instant.now());
    }

    private List<String> codes(FraudResult result) {
        return result.factors().stream().map(RiskFactor::code).toList();
    }

    private List<Transaction> stubs(
            int count, TransactionType type, String amount, Instant createdAt) {
        Customer customer = new Customer("CUST-100001", "Test", "User", "test@example.com", null);
        Account account = new Account("100000000001", customer, AccountType.SAVINGS, "INR");
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Transaction transaction = new Transaction(
                    "TXN-STUB-" + i + "-" + type, "stub-" + type + "-" + i + "-" + System.nanoTime(),
                    account, type, new BigDecimal(amount), "INR",
                    new BigDecimal("1000.00"), new BigDecimal("1010.00"), "stub", 1L);
            setCreatedAt(transaction, createdAt);
            transactions.add(transaction);
        }
        return transactions;
    }

    private void setCreatedAt(Transaction transaction, Instant createdAt) {
        try {
            Field field = Transaction.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(transaction, createdAt);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
