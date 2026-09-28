package com.nexabank.dto;

import com.nexabank.entity.CustomerStatus;

import java.time.Instant;

/**
 * Customer representation returned by the API. Contains no persistence internals.
 */
public record CustomerResponse(
        Long id,
        String customerNumber,
        String firstName,
        String lastName,
        String email,
        String phone,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
