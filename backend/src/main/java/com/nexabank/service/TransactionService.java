package com.nexabank.service;

import com.nexabank.audit.TransactionAuditService;
import com.nexabank.dto.DepositRequest;
import com.nexabank.dto.TransactionPageResponse;
import com.nexabank.dto.TransactionResponse;
import com.nexabank.dto.WithdrawalRequest;
import com.nexabank.entity.Account;
import com.nexabank.entity.AccountStatus;
import com.nexabank.entity.Transaction;
import com.nexabank.entity.TransactionStatus;
import com.nexabank.entity.TransactionType;
import com.nexabank.entity.User;
import com.nexabank.exception.DuplicateIdempotencyKeyException;
import com.nexabank.exception.DuplicateResourceException;
import com.nexabank.exception.InactiveAccountException;
import com.nexabank.exception.InsufficientFundsException;
import com.nexabank.exception.InvalidTransactionException;
import com.nexabank.exception.ResourceNotFoundException;
import com.nexabank.fraud.FraudEvaluationService;
import com.nexabank.fraud.FraudResult;
import com.nexabank.infra.CorrelationIdFilter;
import com.nexabank.infra.NexaBankMetrics;
import com.nexabank.events.DomainEvents;
import com.nexabank.events.OutboxEventType;
import com.nexabank.events.OutboxService;
import com.nexabank.repository.AccountRepository;
import com.nexabank.repository.TransactionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core banking transaction engine: deposits, withdrawals, idempotency,
 * balance mutation and history.
 *
 * <p>Transactional consistency: each money movement runs in one outer
 * {@code @Transactional} unit that (1) locks the account row pessimistically,
 * (2) validates, (3) persists the ledger row in an isolated inner unit,
 * (4) mutates the balance and (5) re-reads the completed row. Any failure
 * rolls everything back, so a balance can never move without its ledger row
 * and vice versa.</p>
 *
 * <p>Concurrency: the account row is loaded with
 * {@code PESSIMISTIC_WRITE} ({@code SELECT ... FOR UPDATE}) and the lock is
 * held until commit, serializing concurrent movements on the same account and
 * preventing lost updates from stale-balance reads.</p>
 *
 * <p>Idempotency is enforced server-side: an exact replay of a known key
 * returns the original result; a conflicting reuse is rejected; a lost race
 * between two concurrent first-seen requests is resolved by the unique
 * constraint, with the loser re-reading the winner's row.</p>
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private static final String SUPPORTED_CURRENCY = "INR";
    private static final int HISTORY_MAX_SIZE = 100;

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CurrentUserService currentUserService;
    private final TransactionAuditService auditService;
    private final FraudEvaluationService fraudEvaluationService;
    private final OutboxService outboxService;
    private final NexaBankMetrics metrics;
    private final ObjectProvider<TransactionService> self;

    public TransactionService(
            TransactionRepository transactions,
            AccountRepository accounts,
            CurrentUserService currentUserService,
            TransactionAuditService auditService,
            FraudEvaluationService fraudEvaluationService,
            OutboxService outboxService,
            NexaBankMetrics metrics,
            ObjectProvider<TransactionService> self) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.fraudEvaluationService = fraudEvaluationService;
        this.outboxService = outboxService;
        this.metrics = metrics;
        this.self = self;
    }

    /**
     * Credits an ACTIVE account. Ownership (CUSTOMER) or staff authorization
     * is verified before any state change.
     */
    @Transactional
    public TransactionResponse deposit(Long accountId, DepositRequest request) {
        NexaBankMetrics.Sample timer = metrics.startTimer();
        try {
            return moveMoney(accountId, request.amount(), trimmed(request.description()),
                    trimmedKey(request.idempotencyKey()), TransactionType.DEPOSIT);
        } finally {
            metrics.recordTransaction(timer);
        }
    }

    /**
     * Debits an ACTIVE account when sufficient funds exist; otherwise the
     * balance is left untouched and a business error is raised.
     */
    @Transactional
    public TransactionResponse withdrawal(Long accountId, WithdrawalRequest request) {
        NexaBankMetrics.Sample timer = metrics.startTimer();
        try {
            return moveMoney(accountId, request.amount(), trimmed(request.description()),
                    trimmedKey(request.idempotencyKey()), TransactionType.WITHDRAWAL);
        } finally {
            metrics.recordTransaction(timer);
        }
    }

    /**
     * Paginated history for one account, newest first. CUSTOMER callers may
     * only read accounts they own; staff follow the existing rules.
     */
    @Transactional(readOnly = true)
    public TransactionPageResponse history(Long accountId, int page, int size) {
        currentUserService.checkAccountAccess(requireAccount(accountId));
        Page<Transaction> result = transactions.findByAccountIdOrderByCreatedAtDescIdDesc(
                accountId, pageable(page, size));
        return toPage(result);
    }

    /**
     * Paginated history across all accounts owned by the authenticated
     * CUSTOMER. Ownership is derived from the JWT identity — never from
     * frontend input.
     */
    @Transactional(readOnly = true)
    public TransactionPageResponse ownHistory(int page, int size) {
        Long customerId = currentUserService.requireCustomerId();
        List<Long> accountIds = accounts.findByCustomerIdOrderByIdAsc(customerId).stream()
                .map(Account::getId)
                .toList();
        if (accountIds.isEmpty()) {
            return new TransactionPageResponse(List.of(), page, boundedSize(size), 0, 0);
        }
        Page<Transaction> result = transactions.findByAccountIdInOrderByCreatedAtDescIdDesc(
                accountIds, pageable(page, size));
        return toPage(result);
    }

    // ---------- engine ----------

    private TransactionResponse moveMoney(
            Long accountId, BigDecimal amount, String description,
            String idempotencyKey, TransactionType type) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Amount must be greater than 0");
        }

        // Fast-path replay: an exact duplicate returns the original result
        // without touching the balance. Conflicting reuse is rejected.
        Optional<Transaction> existing = transactions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), accountId, amount, type);
        }

        // Row lock first: concurrent movements on this account now serialize.
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id " + accountId));
        currentUserService.checkAccountAccess(account);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InactiveAccountException(
                    "Account " + account.getAccountNumber() + " is not ACTIVE and cannot be operated on");
        }
        if (!SUPPORTED_CURRENCY.equals(account.getCurrency())) {
            throw new InvalidTransactionException(
                    "Unsupported account currency: " + account.getCurrency());
        }

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = type == TransactionType.DEPOSIT
                ? balanceBefore.add(amount)
                : balanceBefore.subtract(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: available " + balanceBefore + " " + account.getCurrency());
        }

        User initiator = currentUserService.requireUser();
        String reference = nextReference();
        // Fraud gate: after lock + validation, BEFORE any ledger insert or
        // balance mutation. REVIEW/BLOCK persists its trail independently and
        // throws, so the outer unit rolls back having written nothing — no
        // balance change, no completed ledger row. APPROVE returns its result
        // for post-completion historical persistence below.
        FraudResult fraudResult = fraudEvaluationService.gateSingle(
                account, amount, type, idempotencyKey, reference, initiator.getUsername());
        try {
            // The contested insert runs in its own transaction (see below):
            // a unique-violation there rolls back only that inner unit, so
            // the outer money-movement transaction stays usable and can
            // resolve the race to the winner's row instead of dying with a
            // rollback-only commit failure.
            self.getObject().insertLedgerEntry(
                    account.getId(), reference, idempotencyKey, type, amount,
                    account.getCurrency(), balanceBefore, balanceAfter,
                    description, initiator.getId());
        } catch (DataIntegrityViolationException ex) {
            // Lost the race: a concurrent request with the same key committed
            // first. Re-read the winner and apply replay semantics instead of
            // creating a second financial movement. If the winner is not yet
            // visible, rethrow — the outer unit wrote nothing yet, so it
            // rolls back cleanly into a 409 with no partial state.
            Transaction winner = transactions.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
            log.info("Idempotency race on key '{}' resolved to {}", idempotencyKey,
                    winner.getTransactionReference());
            return replayOrReject(winner, accountId, amount, type);
        }

        account.setBalance(balanceAfter);
        accounts.save(account);

        // Re-read in this unit: the inserted row was committed by the inner
        // transaction and its entity instance belongs to that closed context.
        Transaction completed = transactions.findByTransactionReference(reference)
                .orElseThrow(() -> new IllegalStateException(
                        "Transaction ledger row missing for reference " + reference));
        log.info("{} {} {} on account {}: {} -> {} correlationId={}",
                type, amount, account.getCurrency(), account.getAccountNumber(),
                balanceBefore, balanceAfter, CorrelationIdFilter.current());
        auditService.record(completed, initiator.getUsername());
        // Historical fraud trail for the completed transaction: persisted in
        // this same unit so evaluation + money commit atomically.
        fraudEvaluationService.recordApprovedEvaluation(
                account.getId(), completed.getId(), reference, null, idempotencyKey,
                type, amount, account.getCurrency(), fraudResult, initiator.getUsername());
        // Outbox (same unit): commit ⇒ event exists; rollback ⇒ no event.
        // Kafka publish happens after commit via the scheduled publisher.
        DomainEvents.TransactionCompleted event = new DomainEvents.TransactionCompleted(
                java.util.UUID.randomUUID().toString(), reference, account.getId(),
                account.getAccountNumber(), type.name(), amount, account.getCurrency(),
                completed.getStatus().name(), java.time.Instant.now());
        outboxService.stage(OutboxEventType.TRANSACTION_COMPLETED,
                "Transaction", String.valueOf(completed.getId()), event,
                outboxService.transactionTopic());
        metrics.transaction(type.name().toLowerCase(), "completed");
        return TransactionResponse.from(completed);
    }

    /**
     * Persists one ledger row in an isolated {@code REQUIRES_NEW} unit.
     *
     * <p>Two concurrent first-seen requests with the same idempotency key
     * both pass the replay check; exactly one insert wins and the loser's
     * unique-violation rolls back only this inner unit. The caller's outer
     * transaction never contains a failed flush, so it can never be poisoned
     * into a rollback-only commit failure — it re-reads the winner instead.</p>
     *
     * <p>The account is attached via {@code getReferenceById} (FK id only, no
     * SELECT) because the caller's managed {@code Account} belongs to the
     * outer persistence context and must not leak into this one. Only the
     * generated reference is returned; the caller re-reads the row in its
     * own unit for mapping and auditing.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String insertLedgerEntry(
            Long accountId, String reference, String idempotencyKey, TransactionType type,
            BigDecimal amount, String currency, BigDecimal balanceBefore, BigDecimal balanceAfter,
            String description, Long createdByUserId) {
        Account accountRef = accounts.getReferenceById(accountId);
        Transaction transaction = new Transaction(
                reference, idempotencyKey, accountRef, type, amount, currency,
                balanceBefore, balanceAfter, description, createdByUserId);
        transactions.saveAndFlush(transaction);
        return reference;
    }

    private TransactionResponse replayOrReject(
            Transaction stored, Long accountId, BigDecimal amount, TransactionType type) {
        boolean sameOperation = stored.getAccount().getId().equals(accountId)
                && stored.getType() == type
                && stored.getAmount().compareTo(amount) == 0;
        if (!sameOperation) {
            throw new DuplicateIdempotencyKeyException(
                    "Idempotency key was already used for a different operation");
        }
        if (stored.getStatus() != TransactionStatus.COMPLETED) {
            throw new DuplicateResourceException(
                    "Idempotency key '" + stored.getIdempotencyKey() + "' is already in use");
        }
        // Ownership still applies on replay: a customer must not harvest
        // another customer's ledger rows by guessing idempotency keys.
        currentUserService.checkAccountAccess(stored.getAccount());
        log.info("Idempotent replay of {} returns original result", stored.getTransactionReference());
        return TransactionResponse.from(stored);
    }

    private Account requireAccount(Long accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id " + accountId));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(0, page), boundedSize(size));
    }

    private int boundedSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, HISTORY_MAX_SIZE);
    }

    private TransactionPageResponse toPage(Page<Transaction> page) {
        List<TransactionResponse> content = page.getContent().stream()
                .map(TransactionResponse::from)
                .toList();
        return new TransactionPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * Backend-generated unique reference, e.g. {@code TXN-20260918-000001}.
     * Date prefix keeps references sortable; the random suffix plus the
     * unique constraint keeps generation collision-free without a
     * distributed sequence.
     */
    private String nextReference() {
        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "TXN-%s-%06d".formatted(date, ThreadLocalRandom.current().nextInt(1_000_000));
            if (transactions.findByTransactionReference(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "TXN-%s-%d".formatted(date, System.nanoTime());
    }

    private String trimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String trimmedKey(String key) {
        if (key == null || key.isBlank()) {
            throw new InvalidTransactionException("Idempotency key is required");
        }
        return key.trim();
    }
}
