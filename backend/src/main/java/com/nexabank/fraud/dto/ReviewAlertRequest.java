package com.nexabank.fraud.dto;

import jakarta.validation.constraints.Size;

/** Analyst action payload (optional note). */
public record ReviewAlertRequest(
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason
) {
}
