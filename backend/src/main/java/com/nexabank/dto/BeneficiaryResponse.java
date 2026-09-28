package com.nexabank.dto;

import com.nexabank.entity.Beneficiary;
import com.nexabank.entity.BeneficiaryStatus;

import java.time.Instant;

/**
 * Safe beneficiary projection. Exposes account numbers (needed to transfer)
 * but no private customer details of the destination owner.
 */
public record BeneficiaryResponse(
        Long id,
        Long customerId,
        Long beneficiaryAccountId,
        String beneficiaryAccountNumber,
        String nickname,
        BeneficiaryStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static BeneficiaryResponse from(Beneficiary beneficiary) {
        return new BeneficiaryResponse(
                beneficiary.getId(),
                beneficiary.getCustomer().getId(),
                beneficiary.getBeneficiaryAccount().getId(),
                beneficiary.getBeneficiaryAccount().getAccountNumber(),
                beneficiary.getNickname(),
                beneficiary.getStatus(),
                beneficiary.getCreatedAt(),
                beneficiary.getUpdatedAt());
    }
}
