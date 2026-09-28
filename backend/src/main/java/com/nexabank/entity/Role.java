package com.nexabank.entity;

/**
 * Application roles for role-based authorization.
 * Never represented as free-form strings — always this enum.
 */
public enum Role {
    CUSTOMER,
    BANK_EMPLOYEE,
    FRAUD_ANALYST,
    ADMIN
}
