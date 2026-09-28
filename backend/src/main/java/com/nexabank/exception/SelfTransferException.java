package com.nexabank.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Source and destination accounts are the same. Mapped to HTTP 422.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class SelfTransferException extends RuntimeException {

    public SelfTransferException(String message) {
        super(message);
    }
}
