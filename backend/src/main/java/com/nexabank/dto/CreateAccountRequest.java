package com.nexabank.dto;

import com.nexabank.entity.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for POST /api/v1/accounts. Opening balance is always zero;
 * clients cannot set an arbitrary balance here.
 */
public record CreateAccountRequest(
        @NotNull(message = "Customer ID must not be null")
        Long customerId,

        @NotNull(message = "Account type must not be null (SAVINGS or CHECKING)")
        AccountType accountType,

        @Pattern(regexp = "^INR$", message = "Currency must be INR in this phase")
        String currency
) {
    public CreateAccountRequest {
        if (currency == null || currency.isBlank()) {
            currency = "INR";
        }
    }
}
