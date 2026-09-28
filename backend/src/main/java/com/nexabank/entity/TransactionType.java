package com.nexabank.entity;

/**
 * Financial operation kind. Phase 4 owns DEPOSIT/WITHDRAWAL; Phase 5 adds
 * the two transfer legs. Never represented as free-form strings.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_DEBIT,
    TRANSFER_CREDIT
}
