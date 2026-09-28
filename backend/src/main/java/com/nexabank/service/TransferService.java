package com.nexabank.service;

import com.nexabank.audit.TransactionAuditService;
import com.nexabank.dto.TransferRequest;
import com.nexabank.dto.TransferResponse;
import com.nexabank.entity.Account;
import com.nexabank.entity.AccountStatus;
import com.nexabank.entity.Beneficiary;
import com.nexabank.entity.Transaction;
import com.nexabank.entity.TransactionStatus;
import com.nexabank.entity.TransactionType;
import com.nexabank.entity.User;
import com.nexabank.exception.DuplicateIdempotencyKeyException;
import com.nexabank.exception.DuplicateResourceException;
import com.nexabank.exception.InactiveAccountException;
import com.nexabank.exception.InsufficientFundsException;
import com.nexabank.exception.InvalidTransferException;
import com.nexabank.exception.ResourceNotFoundException;
import com.nexabank.exception.SelfTransferException;
import com.nexabank.fraud.FraudEvaluationService;
import com.nexabank.fraud.FraudResult;
import com.nexabank.infra.CorrelationIdFilter;
import com.nexabank.infra.NexaBankMetrics;
import com.nexabank.events.DomainEvents;
import com.nexabank.events.OutboxEventType;
import com.nexabank.events.OutboxService;
import com.nexabank.repository.AccountRepository;
import com.nexabank.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Account-to-account transfer engine.
 *
 * <p>Atomicity: every validation (ownership, statuses, self-transfer,
 * currency, funds) runs before any write, and the debit leg, credit leg and
 * both balance mutations commit in one outer {@code @Transactional} unit, so
 * a transfer either settles fully or rolls back completely — never a debit
 * without its credit, a credit without its debit, or a lone ledger row.</p>
 *
 * <p>Concurrency: both account rows are locked with {@code PESSIMISTIC_WRITE}
 * in deterministic ascending-id order. Two opposite-direction transfers
 * (A→B vs B→A) therefore contend for the same first lock instead of
 * deadlocking on crossed lock acquisition.</p>
 *
 * <p>Idempotency is server-side: the debit leg carries the client key, the
 * credit leg a derived key; an exact replay returns the original result and
 * a conflicting reuse is rejected. A lost race resolves via the unique
 * constraint (see {@link #insertLegs}).</p>
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private static final String SUPPORTED_CURRENCY = "INR";
    private static final String CREDIT_KEY_SUFFIX = ":credit";

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final BeneficiaryService beneficiaries;
    private final CurrentUserService currentUserService;
    private final TransactionAuditService auditService;
    private final FraudEvaluationService fraudEvaluationService;
    private final OutboxService outboxService;
    private final NexaBankMetrics metrics;
    private final ObjectProvider<TransferService> self;

    public TransferService(
            TransactionRepository transactions,
            AccountRepository accounts,
            BeneficiaryService beneficiaries,
            CurrentUserService currentUserService,
            TransactionAuditService auditService,
            FraudEvaluationService fraudEvaluationService,
            OutboxService outboxService,
            NexaBankMetrics metrics,
            ObjectProvider<TransferService> self) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.beneficiaries = beneficiaries;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.fraudEvaluationService = fraudEvaluationService;
        this.outboxService = outboxService;
        this.metrics = metrics;
        this.self = self;
    }

    /**
     * Moves money from the caller's source account to the beneficiary's
     * destination account. All-or-nothing.
     */
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        NexaBankMetrics.Sample timer = metrics.startTimer();
        try {
            return executeTransfer(request);
        } finally {
            metrics.recordTransfer(timer);
        }
    }

    private TransferResponse executeTransfer(TransferRequest request) {
        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Amount must be greater than 0");
        }
        String idempotencyKey = trimmedKey(request.idempotencyKey());

        // Fast-path replay: exact duplicate returns the original result.
        Optional<Transaction> existing = transactions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), request);
        }

        Beneficiary beneficiary = beneficiaries.requireActiveForTransfer(request.beneficiaryId());
        Long sourceId = request.sourceAccountId();
        Long destinationId = beneficiary.getBeneficiaryAccount().getId();
        if (sourceId.equals(destinationId)) {
            throw new SelfTransferException("Source and destination accounts must be different");
        }

        // Deterministic lock order (ascending id): opposite-direction
        // transfers queue on the same first lock instead of deadlocking.
        List<Account> locked = Stream.of(sourceId, destinationId)
                .sorted()
                .map(id -> accounts.findByIdForUpdate(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Account not found with id " + id)))
                .toList();
        Account source = locked.stream().filter(a -> a.getId().equals(sourceId)).findFirst().orElseThrow();
        Account destination = locked.stream().filter(a -> a.getId().equals(destinationId)).findFirst().orElseThrow();

        currentUserService.checkAccountAccess(source);
        if (source.getStatus() != AccountStatus.ACTIVE) {
            throw new InactiveAccountException(
                    "Account " + source.getAccountNumber() + " is not ACTIVE and cannot send transfers");
        }
        if (destination.getStatus() != AccountStatus.ACTIVE) {
            throw new InactiveAccountException(
                    "Account " + destination.getAccountNumber() + " is not ACTIVE and cannot receive transfers");
        }
        if (!SUPPORTED_CURRENCY.equals(source.getCurrency())
                || !SUPPORTED_CURRENCY.equals(destination.getCurrency())) {
            throw new InvalidTransferException("Transfers are supported in INR only");
        }

        BigDecimal sourceBefore = source.getBalance();
        BigDecimal sourceAfter = sourceBefore.subtract(amount);
        if (sourceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: available " + sourceBefore + " " + source.getCurrency());
        }
        BigDecimal destinationBefore = destination.getBalance();
        BigDecimal destinationAfter = destinationBefore.add(amount);

        User initiator = currentUserService.requireUser();
        String transferReference = nextTransferReference();
        String description = trimmed(request.description());
        // Fraud gate: both accounts are locked deterministically and all
        // validations passed; evaluate BEFORE any debit/credit so a held
        // transfer never partially settles. REVIEW/BLOCK persists its trail
        // independently and throws — outer rolls back having written nothing.
        FraudResult fraudResult = fraudEvaluationService.gateTransfer(
                source, beneficiary, amount, idempotencyKey, transferReference,
                initiator.getUsername());
        try {
            // Both legs persist in one isolated inner unit (see below), so a
            // duplicate-key race rolls back only that unit and the outer
            // transaction stays usable to resolve the winner.
            self.getObject().insertLegs(
                    source.getId(), destination.getId(), transferReference, idempotencyKey,
                    amount, source.getCurrency(), sourceBefore, sourceAfter,
                    destinationBefore, destinationAfter, description, initiator.getId());
        } catch (DataIntegrityViolationException ex) {
            Transaction winner = transactions.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
            log.info("Transfer idempotency race on key '{}' resolved to {}", idempotencyKey,
                    winner.getTransferReference());
            return replayOrReject(winner, request);
        }

        source.setBalance(sourceAfter);
        destination.setBalance(destinationAfter);
        accounts.save(source);
        accounts.save(destination);

        // Re-read in this unit: the legs were committed by the inner
        // transaction and their instances belong to that closed context.
        List<Transaction> legs = transactions.findByTransferReferenceOrderByIdAsc(transferReference);
        Transaction debit = legs.stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER_DEBIT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Transfer debit leg missing for reference " + transferReference));
        Transaction credit = legs.stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER_CREDIT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Transfer credit leg missing for reference " + transferReference));

        log.info("TRANSFER {} {} {} from {} ({} -> {}) to {} ({} -> {}) correlationId={}",
                transferReference, amount, source.getCurrency(), source.getAccountNumber(),
                sourceBefore, sourceAfter, destination.getAccountNumber(),
                destinationBefore, destinationAfter, CorrelationIdFilter.current());
        auditService.recordTransfer(debit, credit, initiator.getUsername());
        // Historical fraud trail for the completed transfer debit leg.
        fraudEvaluationService.recordApprovedEvaluation(
                source.getId(), debit.getId(), debit.getTransactionReference(), transferReference,
                idempotencyKey, TransactionType.TRANSFER_DEBIT, amount, source.getCurrency(),
                fraudResult, initiator.getUsername());
        // Outbox (same unit): both legs + event commit atomically.
        DomainEvents.TransferCompleted event = new DomainEvents.TransferCompleted(
                java.util.UUID.randomUUID().toString(), transferReference, source.getId(),
                destination.getId(), amount, source.getCurrency(), java.time.Instant.now());
        outboxService.stage(OutboxEventType.TRANSFER_COMPLETED,
                "Transfer", transferReference, event, outboxService.transferTopic());
        metrics.transaction("transfer", "completed");
        return buildResponse(debit, credit);
    }

    /**
     * Persists both transfer legs in an isolated {@code REQUIRES_NEW} unit.
     * Either both rows commit or neither does, and a duplicate-key race
     * rolls back only this inner unit — the caller's outer transaction never
     * contains a failed flush, so it can re-read the winner instead of dying
     * with a rollback-only commit failure.
     *
     * <p>Accounts attach via {@code getReferenceById}; the counterparty
     * numbers resolve transparently in this unit. The caller's managed
     * entities belong to the outer persistence context and must not leak
     * into this one, hence ids travel as plain values.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String insertLegs(
            Long sourceId, Long destinationId, String transferReference, String idempotencyKey,
            BigDecimal amount, String currency, BigDecimal sourceBefore, BigDecimal sourceAfter,
            BigDecimal destinationBefore, BigDecimal destinationAfter,
            String description, Long createdByUserId) {
        Account sourceRef = accounts.getReferenceById(sourceId);
        Account destinationRef = accounts.getReferenceById(destinationId);

        Transaction debit = new Transaction(
                transferReference + "-D", idempotencyKey, sourceRef, TransactionType.TRANSFER_DEBIT,
                amount, currency, sourceBefore, sourceAfter, description, createdByUserId);
        debit.setTransferReference(transferReference);
        debit.setCounterpartyAccountNumber(destinationRef.getAccountNumber());

        Transaction credit = new Transaction(
                transferReference + "-C", idempotencyKey + CREDIT_KEY_SUFFIX, destinationRef,
                TransactionType.TRANSFER_CREDIT, amount, currency,
                destinationBefore, destinationAfter, description, createdByUserId);
        credit.setTransferReference(transferReference);
        credit.setCounterpartyAccountNumber(sourceRef.getAccountNumber());

        transactions.save(debit);
        transactions.save(credit);
        transactions.flush();
        return transferReference;
    }

    private TransferResponse replayOrReject(Transaction stored, TransferRequest request) {
        Beneficiary beneficiary = beneficiaries.requireActiveForTransfer(request.beneficiaryId());
        String destinationNumber = beneficiary.getBeneficiaryAccount().getAccountNumber();
        boolean sameOperation = stored.getType() == TransactionType.TRANSFER_DEBIT
                && stored.getAccount().getId().equals(request.sourceAccountId())
                && stored.getAmount().compareTo(request.amount()) == 0
                && destinationNumber.equals(stored.getCounterpartyAccountNumber());
        if (!sameOperation) {
            throw new DuplicateIdempotencyKeyException(
                    "Idempotency key was already used for a different operation");
        }
        if (stored.getStatus() != TransactionStatus.COMPLETED) {
            throw new DuplicateResourceException(
                    "Idempotency key '" + stored.getIdempotencyKey() + "' is already in use");
        }
        // Ownership still applies on replay: key-guessing must not leak
        // another customer's transfer result.
        currentUserService.checkAccountAccess(stored.getAccount());
        List<Transaction> legs = transactions.findByTransferReferenceOrderByIdAsc(
                stored.getTransferReference());
        Transaction credit = legs.stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER_CREDIT)
                .findFirst()
                .orElseThrow(() -> new DuplicateResourceException(
                        "Idempotency key '" + stored.getIdempotencyKey() + "' is already in use"));
        log.info("Transfer idempotent replay of {} returns original result", stored.getTransferReference());
        return buildResponse(stored, credit);
    }

    private TransferResponse buildResponse(Transaction debit, Transaction credit) {
        return new TransferResponse(
                debit.getTransferReference(),
                debit.getAccount().getId(),
                debit.getAccount().getAccountNumber(),
                credit.getAccount().getId(),
                credit.getAccount().getAccountNumber(),
                debit.getAmount(),
                debit.getCurrency(),
                debit.getBalanceBefore(),
                debit.getBalanceAfter(),
                credit.getBalanceBefore(),
                credit.getBalanceAfter(),
                TransactionStatus.COMPLETED,
                debit.getCreatedAt());
    }

    /**
     * Backend-generated unique transfer reference, e.g.
     * {@code TRF-20260918-000001}. The unique constraint plus this
     * pre-check keeps generation collision-free without a distributed
     * sequence.
     */
    private String nextTransferReference() {
        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "TRF-%s-%06d".formatted(date, ThreadLocalRandom.current().nextInt(1_000_000));
            if (transactions.findByTransferReferenceOrderByIdAsc(candidate).isEmpty()
                    && transactions.findByTransactionReference(candidate + "-D").isEmpty()) {
                return candidate;
            }
        }
        return "TRF-%s-%d".formatted(date, System.nanoTime());
    }

    private String trimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String trimmedKey(String key) {
        if (key == null || key.isBlank()) {
            throw new InvalidTransferException("Idempotency key is required");
        }
        return key.trim();
    }
}
