package com.nexabank.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for PUT /api/v1/customers/{id}. All fields are updatable;
 * status changes go through dedicated flows (DELETE deactivates).
 */
public record UpdateCustomerRequest(
        @NotBlank(message = "First name must not be blank")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last name must not be blank")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Pattern(
                regexp = "^$|^[+()\\-\\s\\d]{7,32}$",
                message = "Phone must contain 7-32 digits and may include +, spaces, hyphens or parentheses"
        )
        @Size(max = 32, message = "Phone must be at most 32 characters")
        String phone
) {
}
