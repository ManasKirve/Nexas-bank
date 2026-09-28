package com.nexabank.exception;

/**
 * Thrown on nonsensical state transitions (e.g. CLOSED account reactivated).
 * Maps to HTTP 400.
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
