package com.nexabank.dto;

import com.nexabank.entity.Role;
import com.nexabank.entity.User;
import com.nexabank.entity.UserStatus;

/**
 * Safe user projection for API responses.
 * Never includes the password hash.
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        Role role,
        UserStatus status,
        Long customerId
) {
    public static UserResponse from(User user) {
        Long customerId = user.getCustomer() != null ? user.getCustomer().getId() : null;
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                customerId);
    }
}
