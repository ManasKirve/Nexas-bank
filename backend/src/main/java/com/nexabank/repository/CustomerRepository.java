package com.nexabank.repository;

import com.nexabank.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Customer persistence. Business rules live in {@code CustomerService};
 * this interface stays a thin query contract.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerNumber(String customerNumber);

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCustomerNumber(String customerNumber);

    /**
     * Highest allocated customer-number suffix (e.g. 100003 for CUST-100003),
     * or empty when no customers exist. Used to seed the next number.
     */
    @Query("SELECT MAX(c.customerNumber) FROM Customer c WHERE c.customerNumber LIKE 'CUST-%'")
    Optional<String> findMaxCustomerNumber();

    List<Customer> findAllByOrderByIdAsc();
}
