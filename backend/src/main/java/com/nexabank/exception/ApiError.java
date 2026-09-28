package com.nexabank.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Standard API error body. All controllers return this shape on failure
 * via {@link GlobalExceptionHandler}. Field-level validation problems
 * are reported in {@code errors}; it is null for non-validation errors.
 * {@code correlationId} echoes the request's {@code X-Correlation-Id} so
 * clients can reference failures in support requests.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> errors,
        String correlationId
) {
    public ApiError(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null, null);
    }

    public ApiError(
            Instant timestamp, int status, String error, String message,
            String path, Map<String, String> errors) {
        this(timestamp, status, error, message, path, errors, null);
    }

    public ApiError withCorrelationId(String correlationId) {
        return new ApiError(timestamp, status, error, message, path, errors, correlationId);
    }
}
