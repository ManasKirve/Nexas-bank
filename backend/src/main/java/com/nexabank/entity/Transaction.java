package com.nexabank.entity;

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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable financial ledger record.
 *
 * <p>Every successful deposit/withdrawal writes exactly one row capturing
 * {@code balanceBefore}/{@code balanceAfter} so the ledger is independently
 * auditable against account balances. Monetary values are
 * {@link BigDecimal} (DECIMAL(19,2)) — never double/float.</p>
 *
 * <p>Completed rows are never updated or deleted by normal operations; a
 * future reversal must create a separate compensating transaction.</p>
 */
@Entity
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_transactions_reference", columnNames = "transaction_reference"),
                @UniqueConstraint(name = "uk_transactions_idempotency_key", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "idx_transactions_account_id", columnList = "account_id"),
                @Index(name = "idx_transactions_created_at", columnList = "created_at"),
                @Index(name = "idx_transactions_account_created", columnList = "account_id,created_at"),
                @Index(name = "idx_transactions_transfer_ref", columnList = "transfer_reference")
        }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 32)
    private String transactionReference;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transactions_account"))
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Shared transfer reference (e.g. {@code TRF-20260918-000001}) linking
     * the debit and credit legs of one transfer. Null for deposits and
     * withdrawals, which stand alone.
     */
    @Column(name = "transfer_reference", length = 32)
    private String transferReference;

    /**
     * Account number of the other side of a transfer (destination for debits,
     * source for credits). Lets history show transfer context without
     * exposing the counterparty's private customer details. Null for
     * deposits/withdrawals.
     */
    @Column(name = "counterparty_account_number", length = 24)
    private String counterpartyAccountNumber;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Transaction() {
    }

    public Transaction(
            String transactionReference,
            String idempotencyKey,
            Account account,
            TransactionType type,
            BigDecimal amount,
            String currency,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String description,
            Long createdByUserId) {
        this.transactionReference = transactionReference;
        this.idempotencyKey = idempotencyKey;
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdByUserId = createdByUserId;
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Account getAccount() {
        return account;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTransferReference() {
        return transferReference;
    }

    public void setTransferReference(String transferReference) {
        this.transferReference = transferReference;
    }

    public String getCounterpartyAccountNumber() {
        return counterpartyAccountNumber;
    }

    public void setCounterpartyAccountNumber(String counterpartyAccountNumber) {
        this.counterpartyAccountNumber = counterpartyAccountNumber;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
