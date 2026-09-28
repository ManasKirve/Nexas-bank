package com.nexabank.exception;

/**
 * A supporting infrastructure dependency (Redis/Kafka) needed for the
 * request is unavailable. Mapped to 503 with a generic customer-safe
 * message — connection details stay in the server log.
 */
public class InfraUnavailableException extends RuntimeException {
    public InfraUnavailableException(String message) {
        super(message);
    }

    public InfraUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
