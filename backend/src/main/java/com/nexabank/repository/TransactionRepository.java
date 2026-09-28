package com.nexabank.repository;

import com.nexabank.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Transaction ledger persistence. History queries are newest-first;
 * callers needing pages use the {@link Pageable} variants.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionReference(String transactionReference);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByTransferReferenceOrderByIdAsc(String transferReference);

    List<Transaction> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);

    Page<Transaction> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId, Pageable pageable);

    List<Transaction> findByAccountIdInOrderByCreatedAtDescIdDesc(List<Long> accountIds);

    Page<Transaction> findByAccountIdInOrderByCreatedAtDescIdDesc(List<Long> accountIds, Pageable pageable);

    /**
     * Bounded recent history for fraud evaluation (newest first). Callers
     * pass a small page size — never the full history.
     */
    Page<Transaction> findByAccountIdAndCreatedAtAfterOrderByCreatedAtDescIdDesc(
            Long accountId, Instant after, Pageable pageable);

    long countByAccountIdAndCreatedAtAfter(Long accountId, Instant after);
}
