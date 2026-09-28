package com.nexabank.exception;

/**
 * Thrown when a requested domain object does not exist. Maps to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
