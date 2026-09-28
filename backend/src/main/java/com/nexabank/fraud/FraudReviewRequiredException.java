package com.nexabank.fraud;

/** Thrown when the fraud engine holds a transaction for REVIEW. Mapped to 422 with a generic customer message. */
public class FraudReviewRequiredException extends RuntimeException {
    public FraudReviewRequiredException(String message) {
        super(message);
    }
}
