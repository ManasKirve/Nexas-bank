package com.nexabank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Deposit payload. The client supplies an idempotency key; the backend
 * generates the transaction reference.
 */
public record DepositRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @NotBlank(message = "Idempotency key is required")
        @Size(max = 128, message = "Idempotency key must be at most 128 characters")
        String idempotencyKey
) {
}
