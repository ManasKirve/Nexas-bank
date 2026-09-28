package com.nexabank.exception;

/**
 * Thrown on uniqueness violations (duplicate email, number, ...). Maps to HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
