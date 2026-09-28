package com.nexabank.fraud;

import com.nexabank.entity.Account;
import com.nexabank.entity.Transaction;
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

import java.time.Instant;

/**
 * Analyst work item created only for REVIEW/BLOCK evaluations.
 * Customers never see these rows — only FRAUD_ANALYST and ADMIN.
 */
@Entity
@Table(
        name = "fraud_alerts",
        indexes = {
                @Index(name = "idx_fraud_alert_status", columnList = "status"),
                @Index(name = "idx_fraud_alert_severity", columnList = "severity"),
                @Index(name = "idx_fraud_alert_created_at", columnList = "created_at"),
                @Index(name = "idx_fraud_alert_evaluation_id", columnList = "evaluation_id")
        }
)
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fraud_alert_evaluation"))
    private FraudEvaluation fraudEvaluation;

    /**
     * Completed ledger row when one exists (APPROVE never creates alerts, so
     * this is normally null for held attempts — kept for future linkage).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", foreignKey = @ForeignKey(name = "fk_fraud_alert_transaction"))
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_fraud_alert_account"))
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private FraudAlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FraudAlertStatus status = FraudAlertStatus.OPEN;

    @Column(name = "reason", length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    protected FraudAlert() {
    }

    public Long getId() {
        return id;
    }

    public FraudEvaluation getFraudEvaluation() {
        return fraudEvaluation;
    }

    public void setFraudEvaluation(FraudEvaluation fraudEvaluation) {
        this.fraudEvaluation = fraudEvaluation;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public FraudAlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(FraudAlertSeverity severity) {
        this.severity = severity;
    }

    public FraudAlertStatus getStatus() {
        return status;
    }

    public void setStatus(FraudAlertStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }
}
