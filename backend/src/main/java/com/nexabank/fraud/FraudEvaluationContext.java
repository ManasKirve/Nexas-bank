package com.nexabank.fraud;

import com.nexabank.entity.Transaction;
import com.nexabank.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Pre-loaded fraud evaluation context.
 *
 * <p>Recent transactions are loaded ONCE by the evaluation service with
 * bounded queries (never the full history) and shared across all rules, so
 * N rules do not produce N queries. Rules must not query the database
 * themselves.</p>
 *
 * @param accountId             account the money would move on (source for transfers)
 * @param accountNumber         account number for audit readability
 * @param amount                attempted amount
 * @param type                  attempted transaction type
 * @param beneficiaryId         beneficiary for transfers, null otherwise
 * @param beneficiaryCreatedAt  beneficiary creation time for transfers, null otherwise
 * @param recentTransactions    bounded recent COMPLETED transactions for the account, newest first
 * @param now                   evaluation timestamp
 */
public record FraudEvaluationContext(
        Long accountId,
        String accountNumber,
        BigDecimal amount,
        TransactionType type,
        Long beneficiaryId,
        Instant beneficiaryCreatedAt,
        List<Transaction> recentTransactions,
        Instant now
) {
    public FraudEvaluationContext {
        recentTransactions = recentTransactions == null ? List.of() : List.copyOf(recentTransactions);
    }
}
