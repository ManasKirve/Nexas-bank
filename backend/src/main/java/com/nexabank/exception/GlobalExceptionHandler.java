package com.nexabank.exception;

import com.nexabank.fraud.FraudBlockedException;
import com.nexabank.fraud.FraudReviewRequiredException;
import com.nexabank.infra.CorrelationIdFilter;
import com.nexabank.infra.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Centralized exception handling. Controllers stay thin; all error mapping lives here.
 * No stack traces are exposed to clients — details go to the server log only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), ex);
    }

    @ExceptionHandler({DuplicateResourceException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest request) {
        String message = ex instanceof DuplicateResourceException
                ? ex.getMessage()
                : "Resource already exists";
        return build(HttpStatus.CONFLICT, message, request.getRequestURI(), ex);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(
            InvalidStateTransitionException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), ex);
    }

    @ExceptionHandler({
            InsufficientFundsException.class,
            InvalidTransactionException.class,
            InactiveAccountException.class,
            InvalidTransferException.class,
            SelfTransferException.class,
            FraudBlockedException.class,
            FraudReviewRequiredException.class
    })
    public ResponseEntity<ApiError> handleTransactionBusinessError(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI(), ex);
    }

    @ExceptionHandler(DuplicateIdempotencyKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateIdempotencyKey(
            DuplicateIdempotencyKeyException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI(), ex);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> response =
                build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(response.getBody());
    }

    @ExceptionHandler(InfraUnavailableException.class)
    public ResponseEntity<ApiError> handleInfraUnavailable(
            InfraUnavailableException ex, HttpServletRequest request) {
        // Customer-safe generic message; details stay in the server log.
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "A supporting service is temporarily unavailable. Please try again shortly.",
                request.getRequestURI(), ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        log.warn("Validation failed: {} {} correlationId={}",
                HttpStatus.BAD_REQUEST.value(), request.getRequestURI(), CorrelationIdFilter.current());
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                request.getRequestURI(),
                errors,
                CorrelationIdFilter.current());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request", request.getRequestURI(), ex);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailed(
            AuthenticationFailedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI(), ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI(), ex);
    }

    @ExceptionHandler({ForbiddenOperationException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleForbidden(RuntimeException ex, HttpServletRequest request) {
        String message = ex instanceof ForbiddenOperationException
                ? ex.getMessage()
                : "You do not have permission to access this resource";
        return build(HttpStatus.FORBIDDEN, message, request.getRequestURI(), ex);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No such endpoint", request.getRequestURI(), ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint",
                request.getRequestURI(), ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request.getRequestURI(), ex);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, String path, Exception ex) {
        log.error("Request failed: {} {} correlationId={}", status.value(), path,
                CorrelationIdFilter.current(), ex);
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path,
                null, CorrelationIdFilter.current());
        return ResponseEntity.status(status).body(body);
    }
}
