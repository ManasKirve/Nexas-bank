package com.nexabank.audit;

import com.nexabank.entity.Transaction;
import com.nexabank.fraud.FraudDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Financial-operation audit trail.
 *
 * <p>Every successful deposit/withdrawal emits one structured audit record
 * with the transaction reference, account, initiating user, type, amount and
 * resulting status. Records never contain passwords, JWTs or other
 * authentication secrets. This phase logs to the application log (a
 * dedicated audit appender can be added later); a persistent audit table
 * belongs to a future phase.</p>
 */
@Service
public class TransactionAuditService {

    private static final Logger auditLog = LoggerFactory.getLogger("com.nexabank.audit");

    /**
     * Records a completed financial transaction.
     */
    public void record(Transaction transaction, String username) {
        auditLog.info(
                "AUDIT transactionRef={} accountId={} accountNumber={} type={} amount={} {} balanceBefore={} balanceAfter={} status={} initiatedBy={} initiatedByUserId={}",
                transaction.getTransactionReference(),
                transaction.getAccount().getId(),
                transaction.getAccount().getAccountNumber(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getStatus(),
                username,
                transaction.getCreatedByUserId());
    }

    /**
     * Records a fraud evaluation outcome. Never logs passwords, JWTs or
     * secrets — only the reference, account, score, decision, factor count
     * and evaluation version.
     */
    public void recordFraudEvaluation(
            String transactionReference, Long accountId, int riskScore,
            FraudDecision decision, String evaluationVersion, int factorCount, String username) {
        auditLog.info(
                "AUDIT fraudEvaluation transactionRef={} accountId={} riskScore={} decision={} factors={} version={} evaluatedBy={}",
                transactionReference, accountId, riskScore, decision, factorCount,
                evaluationVersion, username);
    }

    /**
     * Records a completed account-to-account transfer (both legs).
     */
    public void recordTransfer(Transaction debit, Transaction credit, String username) {
        auditLog.info(
                "AUDIT transferRef={} sourceAccountId={} sourceAccountNumber={} destinationAccountId={} destinationAccountNumber={} amount={} {} sourceBalanceBefore={} sourceBalanceAfter={} destinationBalanceBefore={} destinationBalanceAfter={} status={} initiatedBy={} initiatedByUserId={}",
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
                debit.getStatus(),
                username,
                debit.getCreatedByUserId());
    }
}
