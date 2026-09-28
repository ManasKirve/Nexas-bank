import { Injectable, inject } from '@angular/core';
import { Account } from '../../shared/models/account.model';
import {
  FraudAlert,
  FraudAlertSeverity,
  FraudEvaluation,
} from '../../shared/models/fraud.model';
import { DemoRepositoryService } from './demo-repository.service';
import {
  EvaluableType,
  FraudOutcome,
  FraudRuleEngine,
} from './fraud-rule-engine.service';
import { ERRORS, demoError, nextReference } from './demo-api';
import { DEMO_ANALYST } from '../data/demo-data';

/** How many recent movements the engine is allowed to look at per attempt. */
const RULE_BASELINE_LIMIT = 20;

/**
 * The money-movement pipeline shared by deposits, withdrawals and transfers.
 *
 * This is the local equivalent of the backend's `TransactionService` /
 * `TransferService` + `FraudEvaluationService` trio. Keeping it in one place
 * guarantees the three entry points enforce identical rules:
 *
 * 1. the account must exist and be ACTIVE;
 * 2. the amount must be positive with at most 2 decimals;
 * 3. the fraud engine evaluates the attempt against recent history;
 * 4. APPROVE settles the movement; REVIEW/BLOCK hold it — no ledger row, no
 *    balance change — and raise a fraud alert instead.
 */
@Injectable({ providedIn: 'root' })
export class LedgerService {
  private readonly repo = inject(DemoRepositoryService);
  private readonly engine = inject(FraudRuleEngine);

  requireActiveAccount(accountId: number): Account {
    const account = this.repo.accounts().find((a) => a.id === accountId);
    if (!account) {
      throw ERRORS.notFound('Account', accountId);
    }
    if (account.status !== 'ACTIVE') {
      throw ERRORS.badState(
        `Account ${account.accountNumber} is ${account.status} and cannot be used for money movements`,
      );
    }
    return account;
  }

  requireFunds(account: Account, amount: number): void {
    if (account.balance < amount) {
      throw ERRORS.invalid(
        `Insufficient funds in ${account.accountNumber}: balance ${account.balance}, requested ${amount}`,
      );
    }
  }

  /**
   * Builds the rule context for one attempt: the account's own completed
   * movements, newest first and bounded, plus the payee's age for the
   * new-beneficiary rule.
   */
  private buildContext(
    account: Account,
    amount: number,
    type: EvaluableType,
    beneficiaryId: number | null,
    now: Date,
  ) {
    const recent = this.repo
      .transactions()
      .filter((t) => t.accountId === account.id && t.status === 'COMPLETED')
      .slice(-RULE_BASELINE_LIMIT)
      .reverse();
    const beneficiary = beneficiaryId !== null
      ? this.repo.beneficiaries().find((b) => b.id === beneficiaryId)
      : undefined;
    return {
      accountId: account.id,
      accountNumber: account.accountNumber,
      amount,
      type,
      beneficiaryId: beneficiary?.id ?? null,
      beneficiaryCreatedAt: beneficiary?.createdAt ?? null,
      recentTransactions: recent.map((t) => ({
        transactionType: t.transactionType,
        amount: t.amount,
        createdAt: t.createdAt,
      })),
      now,
    };
  }

  /**
   * Evaluates an attempt and always persists the evaluation — an APPROVE is
   * recorded too, so the analyst screens show the full decision history.
   */
  evaluateAttempt(
    account: Account,
    amount: number,
    type: EvaluableType,
    beneficiaryId: number | null,
    now: Date,
    attemptedReference: string,
  ): { outcome: FraudOutcome; evaluation: FraudEvaluation } {
    const outcome = this.engine.evaluate(
      this.buildContext(account, amount, type, beneficiaryId, now),
    );
    const evaluations = this.repo.fraudEvaluations();
    const evaluation: FraudEvaluation = {
      id: evaluations.reduce((max, e) => Math.max(max, e.id), 0) + 1,
      accountId: account.id,
      accountNumber: account.accountNumber,
      transactionId: null,
      attemptedReference,
      transferReference: type === 'TRANSFER_DEBIT' ? attemptedReference : null,
      transactionType: type,
      amount,
      currency: account.currency,
      riskScore: outcome.riskScore,
      riskLevel: outcome.riskLevel,
      decision: outcome.decision,
      factors: outcome.factors,
      evaluationVersion: this.engine.config.evaluationVersion,
      evaluationReason: this.engine.reasonFor(outcome),
      evaluatedBy: DEMO_ANALYST,
      evaluatedAt: now.toISOString(),
    };
    this.repo.saveFraudEvaluations([...evaluations, evaluation]);
    return { outcome, evaluation };
  }

  /** Raises and stores a fraud alert for a held attempt. */
  raiseAlert(
    evaluation: FraudEvaluation,
    outcome: FraudOutcome,
    status: FraudAlert['status'] = 'OPEN',
    reason: string | null = null,
  ): FraudAlert {
    const alerts = this.repo.fraudAlerts();
    const alert: FraudAlert = {
      id: alerts.reduce((max, a) => Math.max(max, a.id), 0) + 1,
      severity: outcome.riskLevel as FraudAlertSeverity,
      status,
      reason: reason ?? evaluation.evaluationReason,
      createdAt: evaluation.evaluatedAt,
      reviewedAt: null,
      reviewedBy: null,
      accountId: evaluation.accountId,
      accountNumber: evaluation.accountNumber,
      transactionId: null,
      evaluationId: evaluation.id,
      riskScore: evaluation.riskScore,
      decision: evaluation.decision,
      riskLevel: evaluation.riskLevel,
      amount: evaluation.amount,
      currency: evaluation.currency,
      attemptedReference: evaluation.attemptedReference,
      transferReference: evaluation.transferReference,
      evaluationVersion: evaluation.evaluationVersion,
      factors: evaluation.factors,
    };
    this.repo.saveFraudAlerts([alert, ...alerts]);
    this.repo.appendActivity(
      'FRAUD_ALERT',
      `${outcome.decision} held ${evaluation.transactionType} of ${evaluation.amount} ${evaluation.currency} on ${evaluation.accountNumber}`,
      evaluation.attemptedReference,
      evaluation.evaluationReason,
    );
    return alert;
  }

  /**
   * The gate every money movement passes through.
   *
   * APPROVE runs `settle` and returns its value. REVIEW and BLOCK raise the
   * fraud alert and fail with 422, leaving the ledger untouched — exactly what
   * the backend did when the engine returned anything other than APPROVE.
   */
  attempt<T>(options: {
    account: Account;
    amount: number;
    type: EvaluableType;
    beneficiaryId?: number | null;
    now?: Date;
    settle: (reference: string) => T;
  }): T {
    const now = options.now ?? new Date();
    // The reference generator needs the references already in the store, or the
    // random suffix could collide with an existing row.
    const existing = this.repo.transactions();
    const taken =
      options.type === 'TRANSFER_DEBIT'
        ? existing
            .map((t) => t.transferReference)
            .filter((r): r is string => !!r)
        : existing.map((t) => t.transactionReference);
    const reference = nextReference(
      options.type === 'TRANSFER_DEBIT' ? 'TRF' : 'TXN',
      now,
      taken,
    );
    const { outcome, evaluation } = this.evaluateAttempt(
      options.account,
      options.amount,
      options.type,
      options.beneficiaryId ?? null,
      now,
      reference,
    );

    if (outcome.decision !== 'APPROVE') {
      const alert = this.raiseAlert(evaluation, outcome);
      throw this.heldError(evaluation, outcome, alert.id);
    }

    const result = options.settle(reference);
    this.repo.appendActivity(
      options.type === 'DEPOSIT'
        ? 'DEPOSIT'
        : options.type === 'WITHDRAWAL'
          ? 'WITHDRAWAL'
          : 'TRANSFER',
      `APPROVE ${options.type} of ${options.amount} ${options.account.currency} on ${options.account.accountNumber}`,
      reference,
    );
    return result;
  }

  /** 422 carrying the decision, score and alert id so the UI can explain itself. */
  private heldError(
    evaluation: FraudEvaluation,
    outcome: FraudOutcome,
    alertId: number,
  ) {
    return demoError(
      422,
      `${outcome.decision}: transaction held by fraud rules (score ${outcome.riskScore}, alert #${alertId}). ${evaluation.evaluationReason}`,
    );
  }
}
