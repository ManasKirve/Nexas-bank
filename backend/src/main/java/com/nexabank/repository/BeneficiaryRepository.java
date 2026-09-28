package com.nexabank.repository;

import com.nexabank.entity.Beneficiary;
import com.nexabank.entity.BeneficiaryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Beneficiary persistence, always scoped to the owning customer.
 */
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    List<Beneficiary> findByCustomerIdOrderByIdAsc(Long customerId);

    List<Beneficiary> findByCustomerIdAndStatusOrderByIdAsc(Long customerId, BeneficiaryStatus status);

    Optional<Beneficiary> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Beneficiary> findByCustomerIdAndBeneficiaryAccountId(Long customerId, Long accountId);
}
