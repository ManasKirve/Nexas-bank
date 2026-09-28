package com.nexabank.infra;

/**
 * Thrown when a caller exceeds its configured rate limit. Mapped to
 * HTTP 429 with a {@code Retry-After} header (seconds until the window
 * resets). Never carries internal state.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
