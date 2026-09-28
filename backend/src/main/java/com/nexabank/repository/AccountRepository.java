package com.nexabank.repository;

import com.nexabank.entity.Account;
import com.nexabank.entity.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Account persistence.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Loads an account with a pessimistic write (row-level) lock for balance
     * mutation. The lock is held until the surrounding transaction commits,
     * so two concurrent deposits/withdrawals on the same account serialize
     * instead of both reading the same stale balance (lost update).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(Long id);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findByCustomerIdOrderByIdAsc(Long customerId);

    List<Account> findAllByOrderByIdAsc();

    long countByStatus(AccountStatus status);

    /**
     * Highest allocated 12-digit account number, or empty when none exist.
     */
    @Query("SELECT MAX(a.accountNumber) FROM Account a")
    Optional<String> findMaxAccountNumber();
}
