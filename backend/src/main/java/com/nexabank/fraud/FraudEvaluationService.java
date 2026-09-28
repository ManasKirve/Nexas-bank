package com.nexabank.fraud;

import com.nexabank.audit.TransactionAuditService;
import com.nexabank.entity.Account;
import com.nexabank.entity.Beneficiary;
import com.nexabank.entity.Transaction;
import com.nexabank.entity.TransactionType;
import com.nexabank.events.DomainEvents;
import com.nexabank.events.OutboxEventType;
import com.nexabank.events.OutboxService;
import com.nexabank.infra.CorrelationIdFilter;
import com.nexabank.infra.NexaBankMetrics;
import com.nexabank.repository.AccountRepository;
import com.nexabank.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Fraud evaluation flow.
 *
 * <p>Ordering guarantee: called by TransactionService/TransferService AFTER
 * pessimistic locking + validation and BEFORE any balance mutation or ledger
 * insert. The engine itself never mutates balances.</p>
 *
 * <p>Safe behavior for held transactions: REVIEW/BLOCK evaluations persist
 * (evaluation + alert) in an isolated {@code REQUIRES_NEW} unit and then a
 * generic exception stops the money movement. Because persistence commits
 * independently, the outer rollback (triggered by the exception) removes no
 * fraud trail while still guaranteeing no balance mutation and no completed
 * ledger row.</p>
 *
 * <p>APPROVE evaluations persist in the caller's transaction linked to the
 * completed ledger row, so evaluation and money movement commit atomically.</p>
 */
@Service
public class FraudEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(FraudEvaluationService.class);

    /** Generic customer-safe message — never exposes score, rules or factors. */
    public static final String CUSTOMER_SAFE_MESSAGE = "Your transaction could not be completed at this time.";

    private final FraudRuleEngine engine;
    private final FraudProperties properties;
    private final FraudEvaluationRepository evaluations;
    private final FraudAlertRepository alerts;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final TransactionAuditService auditService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<FraudEvaluationService> self;
    private final OutboxService outboxService;
    private final NexaBankMetrics metrics;

    public FraudEvaluationService(
            FraudRuleEngine engine,
            FraudProperties properties,
            FraudEvaluationRepository evaluations,
            FraudAlertRepository alerts,
            TransactionRepository transactions,
            AccountRepository accounts,
            TransactionAuditService auditService,
            ObjectMapper objectMapper,
            ObjectProvider<FraudEvaluationService> self,
            OutboxService outboxService,
            NexaBankMetrics metrics) {
        this.engine = engine;
        this.properties = properties;
        this.evaluations = evaluations;
        this.alerts = alerts;
        this.transactions = transactions;
        this.accounts = accounts;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.self = self;
        this.outboxService = outboxService;
        this.metrics = metrics;
    }

    // ---------- context loading (bounded, single fetch per evaluation) ----------

    /**
     * Loads the bounded recent history for one account and evaluates the
     * attempted single-leg operation (deposit/withdrawal). Pure evaluation —
     * no persistence.
     */
    public FraudResult evaluateSingle(
            Account account, BigDecimal amount, TransactionType type) {
        NexaBankMetrics.Sample timer = metrics.startTimer();
        try {
            FraudEvaluationContext context = contextFor(account, amount, type, null);
            return engine.evaluate(context);
        } finally {
            metrics.recordFraudEvaluation(timer);
        }
    }

    /**
     * Loads context and evaluates a transfer debit attempt. Pure evaluation —
     * no persistence.
     */
    public FraudResult evaluateTransfer(
            Account source, Beneficiary beneficiary, BigDecimal amount) {
        NexaBankMetrics.Sample timer = metrics.startTimer();
        try {
            FraudEvaluationContext context = contextFor(
                    source, amount, TransactionType.TRANSFER_DEBIT, beneficiary);
            return engine.evaluate(context);
        } finally {
            metrics.recordFraudEvaluation(timer);
        }
    }

    /**
     * Gate for deposits/withdrawals. Returns silently on APPROVE; on
     * REVIEW/BLOCK persists the fraud trail independently and throws a
     * generic customer-safe exception that rolls back the outer money
     * movement (which has written nothing yet).
     */
    public FraudResult gateSingle(
            Account account, BigDecimal amount, TransactionType type,
            String idempotencyKey, String attemptedReference, String username) {
        FraudEvaluationContext context = contextFor(account, amount, type, null);
        FraudResult result = engine.evaluate(context);
        if (result.decision() == FraudDecision.APPROVE) {
            log.debug("Fraud APPROVE: accountId={} ref={} score={}",
                    context.accountId(), attemptedReference, result.riskScore());
            return result;
        }
        // Via self-proxy so REQUIRES_NEW actually applies (self-invocation
        // would silently join the caller's transaction and roll back with it).
        self.getObject().recordHeldEvaluation(
                account.getId(), null, attemptedReference, null, idempotencyKey,
                type, amount, account.getCurrency(), result, username);
        metrics.transaction(type.name().toLowerCase(), "held");
        throwHeld(result);
        throw new IllegalStateException("unreachable");
    }

    /**
     * Gate for transfers. Same guarantees as {@link #gateSingle}.
     */
    public FraudResult gateTransfer(
            Account source, Beneficiary beneficiary, BigDecimal amount,
            String idempotencyKey, String attemptedReference, String username) {
        FraudEvaluationContext context = contextFor(
                source, amount, TransactionType.TRANSFER_DEBIT, beneficiary);
        FraudResult result = engine.evaluate(context);
        if (result.decision() == FraudDecision.APPROVE) {
            log.debug("Fraud APPROVE: accountId={} ref={} score={}",
                    context.accountId(), attemptedReference, result.riskScore());
            return result;
        }
        // Via self-proxy so REQUIRES_NEW actually applies (self-invocation
        // would silently join the caller's transaction and roll back with it).
        self.getObject().recordHeldEvaluation(
                source.getId(), null, attemptedReference, attemptedReference, idempotencyKey,
                TransactionType.TRANSFER_DEBIT, amount, source.getCurrency(), result, username);
        metrics.transaction("transfer", "held");
        throwHeld(result);
        throw new IllegalStateException("unreachable");
    }

    /**
     * Persists an APPROVE evaluation linked to the completed ledger row.
     * Called after the money movement commits its ledger row, in the
     * caller's transaction, so evaluation + money commit atomically.
     */
    @Transactional
    public FraudEvaluation recordApprovedEvaluation(
            Long accountId, Long transactionId, String transactionReference, String transferReference,
            String idempotencyKey, TransactionType type, BigDecimal amount, String currency,
            FraudResult result, String username) {
        Account accountRef = accounts.getReferenceById(accountId);
        Transaction transactionRef = transactionId == null
                ? null : transactions.getReferenceById(transactionId);
        FraudEvaluation evaluation = new FraudEvaluation();
        evaluation.setAccount(accountRef);
        evaluation.setTransaction(transactionRef);
        evaluation.setAttemptedReference(transactionReference);
        evaluation.setTransferReference(transferReference);
        evaluation.setIdempotencyKey(idempotencyKey);
        evaluation.setTransactionType(type);
        evaluation.setAmount(amount);
        evaluation.setCurrency(currency);
        evaluation.setRiskScore(result.riskScore());
        evaluation.setRiskLevel(result.riskLevel());
        evaluation.setDecision(result.decision());
        evaluation.setFactorsJson(toFactorsJson(result.factors()));
        evaluation.setEvaluationVersion(properties.getEvaluationVersion());
        evaluation.setEvaluationReason(reasonFor(result));
        evaluation.setEvaluatedBy(username);
        FraudEvaluation saved = evaluations.save(evaluation);
        auditService.recordFraudEvaluation(
                transactionReference, accountId, result.riskScore(),
                result.decision(), saved.getEvaluationVersion(), result.factors().size(), username);
        DomainEvents.FraudEvaluationCompleted event = new DomainEvents.FraudEvaluationCompleted(
                java.util.UUID.randomUUID().toString(), saved.getId(), accountId,
                transactionReference, result.riskScore(), result.riskLevel().name(),
                result.decision().name(), saved.getEvaluationVersion(), java.time.Instant.now());
        outboxService.stage(OutboxEventType.FRAUD_EVALUATION_COMPLETED,
                "FraudEvaluation", String.valueOf(saved.getId()), event,
                outboxService.fraudEvaluationTopic());
        metrics.fraudEvaluation(result.decision().name());
        return saved;
    }

    /**
     * Persists a REVIEW/BLOCK evaluation + alert in an isolated unit so the
     * trail survives the outer money-movement rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FraudEvaluation recordHeldEvaluation(
            Long accountId, Long transactionId, String attemptedReference, String transferReference,
            String idempotencyKey, TransactionType type, BigDecimal amount, String currency,
            FraudResult result, String username) {
        Account accountRef = accounts.getReferenceById(accountId);
        Transaction transactionRef = transactionId == null
                ? null : transactions.getReferenceById(transactionId);
        FraudEvaluation evaluation = new FraudEvaluation();
        evaluation.setAccount(accountRef);
        evaluation.setTransaction(transactionRef);
        evaluation.setAttemptedReference(attemptedReference);
        evaluation.setTransferReference(transferReference);
        evaluation.setIdempotencyKey(idempotencyKey);
        evaluation.setTransactionType(type);
        evaluation.setAmount(amount);
        evaluation.setCurrency(currency);
        evaluation.setRiskScore(result.riskScore());
        evaluation.setRiskLevel(result.riskLevel());
        evaluation.setDecision(result.decision());
        evaluation.setFactorsJson(toFactorsJson(result.factors()));
        evaluation.setEvaluationVersion(properties.getEvaluationVersion());
        evaluation.setEvaluationReason(reasonFor(result));
        evaluation.setEvaluatedBy(username);
        FraudEvaluation saved = evaluations.saveAndFlush(evaluation);

        FraudAlert alert = new FraudAlert();
        alert.setFraudEvaluation(saved);
        alert.setTransaction(transactionRef);
        alert.setAccount(accountRef);
        alert.setSeverity(FraudAlertSeverity.fromRiskLevel(result.riskLevel()));
        alert.setStatus(FraudAlertStatus.OPEN);
        alert.setReason(alertReason(result));
        alerts.saveAndFlush(alert);

        log.info("Fraud {} recorded: accountId={} ref={} score={} factors={} alertId={} correlationId={}",
                result.decision(), accountId, attemptedReference, result.riskScore(),
                result.factors().size(), alert.getId(), CorrelationIdFilter.current());
        auditService.recordFraudEvaluation(
                attemptedReference, accountId, result.riskScore(),
                result.decision(), saved.getEvaluationVersion(), result.factors().size(), username);
        DomainEvents.FraudEvaluationCompleted evaluationEvent =
                new DomainEvents.FraudEvaluationCompleted(
                        java.util.UUID.randomUUID().toString(), saved.getId(), accountId,
                        attemptedReference, result.riskScore(), result.riskLevel().name(),
                        result.decision().name(), saved.getEvaluationVersion(),
                        java.time.Instant.now());
        outboxService.stage(OutboxEventType.FRAUD_EVALUATION_COMPLETED,
                "FraudEvaluation", String.valueOf(saved.getId()), evaluationEvent,
                outboxService.fraudEvaluationTopic());
        DomainEvents.FraudAlertCreated alertEvent = new DomainEvents.FraudAlertCreated(
                java.util.UUID.randomUUID().toString(), alert.getId(), saved.getId(), accountId,
                alert.getSeverity().name(), result.riskScore(), result.decision().name(),
                java.time.Instant.now());
        outboxService.stage(OutboxEventType.FRAUD_ALERT_CREATED,
                "FraudAlert", String.valueOf(alert.getId()), alertEvent,
                outboxService.fraudAlertTopic());
        metrics.fraudEvaluation(result.decision().name());
        metrics.fraudAlert(alert.getSeverity().name());
        return saved;
    }

    // ---------- internals ----------

    private FraudEvaluationContext contextFor(
            Account account, BigDecimal amount, TransactionType type, Beneficiary beneficiary) {
        // Bounded fetch: only what windowed + spike rules need (never full history).
        int lookback = Math.max(
                properties.getRules().getActivitySpikeLookback(),
                properties.getRules().getHighFrequencyCount() + 5);
        List<Transaction> recent = transactions
                .findByAccountIdOrderByCreatedAtDescIdDesc(
                        account.getId(), PageRequest.of(0, Math.min(50, Math.max(10, lookback))))
                .getContent();
        Instant beneficiaryCreatedAt = beneficiary == null ? null : beneficiary.getCreatedAt();
        Long beneficiaryId = beneficiary == null ? null : beneficiary.getId();
        return new FraudEvaluationContext(
                account.getId(), account.getAccountNumber(), amount, type,
                beneficiaryId, beneficiaryCreatedAt, recent, Instant.now());
    }

    private void throwHeld(FraudResult result) {
        if (result.decision() == FraudDecision.BLOCK) {
            throw new FraudBlockedException(CUSTOMER_SAFE_MESSAGE);
        }
        throw new FraudReviewRequiredException(CUSTOMER_SAFE_MESSAGE);
    }

    private String reasonFor(FraudResult result) {
        if (result.factors().isEmpty()) {
            return "No risk factors triggered (evaluation " + properties.getEvaluationVersion() + ")";
        }
        return "Triggered " + result.factors().size() + " factor(s): "
                + result.factors().stream().map(RiskFactor::code).sorted()
                .reduce((a, b) -> a + "," + b).orElse("")
                + " (evaluation " + properties.getEvaluationVersion() + ")";
    }

    private String alertReason(FraudResult result) {
        return "Decision " + result.decision() + " with score " + result.riskScore()
                + ": " + result.factors().stream().map(RiskFactor::code).sorted()
                .reduce((a, b) -> a + ", " + b).orElse("no factors");
    }

    String toFactorsJson(List<RiskFactor> factors) {
        try {
            return objectMapper.writeValueAsString(factors);
        } catch (Exception ex) {
            log.warn("Failed to serialize fraud factors, storing empty array", ex);
            return "[]";
        }
    }

    public List<RiskFactor> parseFactors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RiskFactor.class));
        } catch (Exception ex) {
            log.warn("Failed to parse fraud factors JSON", ex);
            return List.of();
        }
    }
}
