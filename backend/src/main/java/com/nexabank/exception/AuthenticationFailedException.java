package com.nexabank.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Generic authentication failure (bad credentials, unknown user or
 * inactive account). Always mapped to 401 with a message that does not
 * reveal which part of the credentials was wrong.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Invalid username or password");
    }

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
