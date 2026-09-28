package com.nexabank.dto;

/**
 * Login success payload. Carries the JWT access token plus the
 * safe user projection. Never contains any password material.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
