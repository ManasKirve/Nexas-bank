package com.nexabank.controller;

import com.nexabank.dto.DepositRequest;
import com.nexabank.dto.TransactionPageResponse;
import com.nexabank.dto.TransactionResponse;
import com.nexabank.dto.WithdrawalRequest;
import com.nexabank.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Core banking transaction API. Thin adapter: validation + role gates +
 * service delegation. Ownership is enforced by the backend inside
 * {@code TransactionService} — never trusted from frontend input.
 *
 * <p>Money movement is limited to CUSTOMER (own accounts only),
 * BANK_EMPLOYEE and ADMIN. FRAUD_ANALYST is deliberately excluded:
 * fraud-monitoring access must not imply money-moving permission.
 * No PUT/DELETE endpoints exist — completed ledger rows are immutable.</p>
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/{accountId}/deposits")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable Long accountId, @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.deposit(accountId, request));
    }

    @PostMapping("/{accountId}/withdrawals")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable Long accountId, @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.withdrawal(accountId, request));
    }

    @GetMapping("/{accountId}/transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TransactionPageResponse> history(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(transactionService.history(accountId, page, size));
    }

    /**
     * History across the authenticated CUSTOMER's own accounts. Ownership is
     * derived from the JWT identity — never from frontend-supplied ids.
     * Declared before {@code /{accountId}/transactions} ambiguities cannot
     * arise since {@code me} never parses as an account id path here.
     */
    @GetMapping("/me/transactions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TransactionPageResponse> ownHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(transactionService.ownHistory(page, size));
    }
}
