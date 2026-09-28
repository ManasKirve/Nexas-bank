package com.nexabank.controller;

import com.nexabank.dto.AccountResponse;
import com.nexabank.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Nested customer resource: accounts owned by one customer.
 * CUSTOMER callers must stay within their own linked customer id.
 */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/accounts")
public class CustomerAccountController {

    private final AccountService accountService;

    public CustomerAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AccountResponse>> findByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(accountService.findByCustomerAuthorized(customerId));
    }
}
