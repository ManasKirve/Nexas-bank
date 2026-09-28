package com.nexabank.exception;

/**
 * Referenced beneficiary does not exist. Maps to HTTP 404 via
 * {@link ResourceNotFoundException}.
 */
public class BeneficiaryNotFoundException extends ResourceNotFoundException {

    public BeneficiaryNotFoundException(String message) {
        super(message);
    }
}
