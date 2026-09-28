package com.nexabank.fraud;

import com.nexabank.entity.Account;
import com.nexabank.entity.Transaction;
import com.nexabank.entity.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Historical, immutable fraud evaluation.
 *
 * <p>One row per evaluated transaction attempt — never overwritten. When the
 * decision is APPROVE the row links to the completed ledger {@link Transaction};
 * when REVIEW/BLOCK stops the money movement there is no completed ledger row,
 * so {@code transaction} stays null and the attempt is traced via
 * {@code attemptedReference}, account, amount and idempotency key.</p>
 */
@Entity
@Table(
        name = "fraud_evaluations",
        indexes = {
                @Index(name = "idx_fraud_eval_account_id", columnList = "account_id"),
                @Index(name = "idx_fraud_eval_transaction_id", columnList = "transaction_id"),
                @Index(name = "idx_fraud_eval_decision", columnList = "decision"),
                @Index(name = "idx_fraud_eval_evaluated_at", columnList = "evaluated_at")
        }
)
public class FraudEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fraud_eval_account"))
    private Account account;

    /**
     * Completed ledger row for APPROVE decisions. Null for REVIEW/BLOCK
     * attempts where no financial transaction completed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", foreignKey = @ForeignKey(name = "fk_fraud_eval_transaction"))
    private Transaction transaction;

    @Column(name = "attempted_reference", length = 40)
    private String attemptedReference;

    @Column(name = "transfer_reference", length = 32)
    private String transferReference;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 16)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16)
    private FraudDecision decision;

    /** JSON array of triggered factors: [{code, description, score}]. */
    @Column(name = "factors_json", columnDefinition = "TEXT")
    private String factorsJson = "[]";

    @Column(name = "evaluation_version", nullable = false, length = 16)
    private String evaluationVersion = "v1";

    @Column(name = "evaluation_reason", length = 500)
    private String evaluationReason;

    @Column(name = "evaluated_by", length = 64)
    private String evaluatedBy;

    @CreationTimestamp
    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    protected FraudEvaluation() {
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public String getAttemptedReference() {
        return attemptedReference;
    }

    public void setAttemptedReference(String attemptedReference) {
        this.attemptedReference = attemptedReference;
    }

    public String getTransferReference() {
        return transferReference;
    }

    public void setTransferReference(String transferReference) {
        this.transferReference = transferReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public FraudDecision getDecision() {
        return decision;
    }

    public void setDecision(FraudDecision decision) {
        this.decision = decision;
    }

    public String getFactorsJson() {
        return factorsJson;
    }

    public void setFactorsJson(String factorsJson) {
        this.factorsJson = factorsJson;
    }

    public String getEvaluationVersion() {
        return evaluationVersion;
    }

    public void setEvaluationVersion(String evaluationVersion) {
        this.evaluationVersion = evaluationVersion;
    }

    public String getEvaluationReason() {
        return evaluationReason;
    }

    public void setEvaluationReason(String evaluationReason) {
        this.evaluationReason = evaluationReason;
    }

    public String getEvaluatedBy() {
        return evaluatedBy;
    }

    public void setEvaluatedBy(String evaluatedBy) {
        this.evaluatedBy = evaluatedBy;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
