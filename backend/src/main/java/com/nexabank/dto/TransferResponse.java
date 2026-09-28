package com.nexabank.dto;

import com.nexabank.entity.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transfer result: both legs settle under one shared transfer reference.
 */
public record TransferResponse(
        String transferReference,
        Long sourceAccountId,
        String sourceAccountNumber,
        Long destinationAccountId,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency,
        BigDecimal sourceBalanceBefore,
        BigDecimal sourceBalanceAfter,
        BigDecimal destinationBalanceBefore,
        BigDecimal destinationBalanceAfter,
        TransactionStatus status,
        Instant createdAt
) {
}
