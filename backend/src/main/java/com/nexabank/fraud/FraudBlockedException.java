package com.nexabank.fraud;

/** Thrown when the fraud engine BLOCKs a transaction. Mapped to 422 with a generic customer message. */
public class FraudBlockedException extends RuntimeException {
    public FraudBlockedException(String message) {
        super(message);
    }
}
