package com.nexabank.entity;

/**
 * Lifecycle status of an application {@link User}.
 * Only {@code ACTIVE} users may authenticate.
 */
public enum UserStatus {
    ACTIVE,
    LOCKED,
    DISABLED
}
