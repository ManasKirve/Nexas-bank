package com.nexabank.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload. A single generic error is returned for any
 * credential mismatch to avoid account enumeration.
 */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {
}
