package com.nexabank.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The same idempotency key was reused with a conflicting payload
 * (different account, type or amount). Exact replays return the original
 * result instead — mapped to HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String message) {
        super(message);
    }
}
