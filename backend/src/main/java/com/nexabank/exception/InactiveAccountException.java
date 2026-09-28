package com.nexabank.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Money movement attempted on a non-ACTIVE account. Mapped to HTTP 422 —
 * the account exists and the caller may be authorized, but the business
 * state forbids the operation.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InactiveAccountException extends RuntimeException {

    public InactiveAccountException(String message) {
        super(message);
    }
}
