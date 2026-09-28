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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A named destination account owned by one customer. Transfers may only
 * target a beneficiary that belongs to the authenticated customer (staff
 * roles follow the operational-access model instead).
 *
 * <p>One row per (customer, destination account): re-adding a disabled
 * beneficiary re-activates it instead of duplicating history.</p>
 */
@Entity
@Table(
        name = "beneficiaries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_beneficiaries_customer_account",
                        columnNames = {"customer_id", "beneficiary_account_id"})
        },
        indexes = {
                @Index(name = "idx_beneficiaries_customer_id", columnList = "customer_id"),
                @Index(name = "idx_beneficiaries_account_id", columnList = "beneficiary_account_id"),
                @Index(name = "idx_beneficiaries_status", columnList = "status")
        }
)
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_beneficiaries_customer"))
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_beneficiaries_account"))
    private Account beneficiaryAccount;

    @Column(name = "nickname", nullable = false, length = 64)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BeneficiaryStatus status = BeneficiaryStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Beneficiary() {
    }

    public Beneficiary(Customer customer, Account beneficiaryAccount, String nickname) {
        this.customer = customer;
        this.beneficiaryAccount = beneficiaryAccount;
        this.nickname = nickname;
        this.status = BeneficiaryStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Account getBeneficiaryAccount() {
        return beneficiaryAccount;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public BeneficiaryStatus getStatus() {
        return status;
    }

    public void setStatus(BeneficiaryStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
