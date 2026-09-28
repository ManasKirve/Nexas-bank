package com.nexabank.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Beneficiary creation payload. The owning customer is derived from the
 * authenticated user — never trusted from the frontend.
 */
public record CreateBeneficiaryRequest(
        @NotNull(message = "Beneficiary account id is required")
        Long accountId,

        @Size(max = 64, message = "Nickname must be at most 64 characters")
        String nickname
) {
}
