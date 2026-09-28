package com.nexabank.controller;

import com.nexabank.dto.TransferRequest;
import com.nexabank.dto.TransferResponse;
import com.nexabank.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account-to-account transfer API. Thin adapter: validation + role gate +
 * service delegation. Source and beneficiary ownership are enforced by the
 * backend inside {@code TransferService}.
 *
 * <p>Money movement is limited to CUSTOMER (own accounts/beneficiaries),
 * BANK_EMPLOYEE and ADMIN. FRAUD_ANALYST is deliberately excluded.</p>
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.transfer(request));
    }
}
