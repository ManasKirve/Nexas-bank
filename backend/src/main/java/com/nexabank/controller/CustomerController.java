package com.nexabank.controller;

import com.nexabank.dto.CreateCustomerRequest;
import com.nexabank.dto.CustomerResponse;
import com.nexabank.dto.UpdateCustomerRequest;
import com.nexabank.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer REST API. Thin adapter: validation + authorization + service
 * delegation + status codes.
 *
 * <p>Ownership is enforced by the backend: CUSTOMER users resolve their own
 * profile via {@code /me} or may only read their linked customer id;
 * management operations require BANK_EMPLOYEE or ADMIN.</p>
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<List<CustomerResponse>> findAll() {
        return ResponseEntity.ok(customerService.findAll());
    }

    /**
     * Authenticated customer's own profile. Ownership is derived from the
     * JWT identity — never from a frontend-supplied id.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CustomerResponse> findOwnProfile() {
        return ResponseEntity.ok(customerService.findOwnProfile());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CustomerResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.findByIdAuthorized(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<CustomerResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(customerService.update(id, request));
    }

    /**
     * Soft delete: returns the deactivated customer with HTTP 200.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<CustomerResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.deactivate(id));
    }
}
