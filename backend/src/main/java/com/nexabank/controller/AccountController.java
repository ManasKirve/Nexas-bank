package com.nexabank.controller;

import com.nexabank.dto.AccountResponse;
import com.nexabank.dto.CreateAccountRequest;
import com.nexabank.dto.UpdateAccountStatusRequest;
import com.nexabank.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account REST API. Balances are never accepted or mutated here;
 * new accounts always start at zero.
 *
 * <p>CUSTOMER users only see accounts owned by their linked customer;
 * account management requires BANK_EMPLOYEE or ADMIN.</p>
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<List<AccountResponse>> findAll() {
        return ResponseEntity.ok(accountService.findAll());
    }

    /**
     * Accounts of the authenticated customer. Ownership is derived from
     * the JWT identity — never from a frontend-supplied customer id.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AccountResponse>> findOwnAccounts() {
        return ResponseEntity.ok(accountService.findOwnAccounts());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.findByIdAuthorized(id));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<AccountResponse> changeStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ResponseEntity.ok(accountService.changeStatus(id, request.status()));
    }
}
