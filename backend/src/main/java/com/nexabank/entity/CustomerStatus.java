package com.nexabank.entity;

/**
 * Lifecycle states for a customer.
 * Deletion is soft: DELETE maps to INACTIVE rather than physical removal.
 */
public enum CustomerStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED
}
