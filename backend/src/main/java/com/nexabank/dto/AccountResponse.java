package com.nexabank.dto;

import com.nexabank.entity.AccountStatus;
import com.nexabank.entity.AccountType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Account representation returned by the API.
 */
public record AccountResponse(
        Long id,
        String accountNumber,
        Long customerId,
        String customerNumber,
        AccountType accountType,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
