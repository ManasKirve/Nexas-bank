package com.nexabank.dto;

import com.nexabank.entity.Transaction;
import com.nexabank.entity.TransactionStatus;
import com.nexabank.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Safe ledger projection for API responses. Never exposes JPA entities.
 */
public record TransactionResponse(
        String transactionReference,
        Long accountId,
        String accountNumber,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        TransactionStatus status,
        String description,
        String transferReference,
        String counterpartyAccountNumber,
        Instant createdAt,
        Instant completedAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getTransactionReference(),
                transaction.getAccount().getId(),
                transaction.getAccount().getAccountNumber(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getTransferReference(),
                transaction.getCounterpartyAccountNumber(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt());
    }
}
