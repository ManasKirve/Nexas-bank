package com.nexabank.dto;

import com.nexabank.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for PUT /api/v1/accounts/{id}/status.
 */
public record UpdateAccountStatusRequest(
        @NotNull(message = "Status must not be null (ACTIVE, FROZEN or CLOSED)")
        AccountStatus status
) {
}
