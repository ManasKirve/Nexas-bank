package com.nexabank.controller;

import com.nexabank.dto.BeneficiaryResponse;
import com.nexabank.dto.CreateBeneficiaryRequest;
import com.nexabank.service.BeneficiaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Beneficiary API. Ownership is derived from the authenticated user inside
 * {@code BeneficiaryService}. Fraud analysts have no beneficiary business
 * and are denied here, consistent with money-movement gating.
 */
@RestController
@RequestMapping("/api/v1/beneficiaries")
@PreAuthorize("hasAnyRole('CUSTOMER','BANK_EMPLOYEE','ADMIN')")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> create(@Valid @RequestBody CreateBeneficiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(beneficiaryService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> list() {
        return ResponseEntity.ok(beneficiaryService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BeneficiaryResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(beneficiaryService.get(id));
    }

    /**
     * Soft delete: ACTIVE → DISABLED, history preserved.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<BeneficiaryResponse> disable(@PathVariable Long id) {
        return ResponseEntity.ok(beneficiaryService.disable(id));
    }
}
