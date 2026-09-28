import { Account, AccountStatus, AccountType } from '../../shared/models/account.model';
import { Customer, CustomerStatus } from '../../shared/models/customer.model';
import { Beneficiary, BeneficiaryStatus } from '../../shared/models/beneficiary.model';
import {
  FraudAlert,
  FraudAlertSeverity,
  FraudAlertStatus,
  FraudEvaluation,
} from '../../shared/models/fraud.model';
import { Transaction, TransactionType } from '../../shared/models/transaction.model';
import { DemoActivityEntry, DemoActivityType } from '../../shared/models/audit.model';
import { EvaluableType, FraudRuleEngine } from '../services/fraud-rule-engine.service';
import { nextReference, round2 } from '../services/demo-api';

/**
 * The dataset written to LocalStorage on the very first launch.
 *
 * Design notes:
 *
 * - Balances are never hard-coded. Every balance is the running total of the
 *   seeded movements, exactly like a real ledger, so `balanceAfter` on the last
 *   row always matches the account balance shown in the UI.
 * - Transfers write both legs (debit + credit) under one shared `TRF-…`
 *   reference, mirroring the backend's double-entry transfer.
 * - Every attempt is run through the real `FraudRuleEngine`. A REVIEW or BLOCK
 *   decision is *held*: no ledger row and no balance change, but an evaluation
 *   and a fraud alert are recorded. So the fraud queue is produced by the
 *   engine rather than typed in by hand.
 * - Timestamps are relative to launch time so the windowed rules (rapid
 *   withdrawals/transfers, new beneficiary, activity spike) actually fire.
 */

/** Identity recorded on seeded rows — clearly a demo actor, not a real user. */
export const DEMO_OPERATOR = 'demo.operator@nexabank.demo';
export const DEMO_ANALYST = 'demo.analyst@nexabank.demo';

export interface DemoDataset {
  customers: Customer[];
  accounts: Account[];
  transactions: Transaction[];
  beneficiaries: Beneficiary[];
  fraudAlerts: FraudAlert[];
  fraudEvaluations: FraudEvaluation[];
  auditLog: DemoActivityEntry[];
  /** Customer the "my money" dashboard views act on behalf of. */
  activeCustomerId: number;
}

const CUSTOMER_SEED: Array<{
  id: number;
  number: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  status: CustomerStatus;
  daysAgo: number;
}> = [
  {
    id: 1,
    number: 'CUST-100001',
    firstName: 'Ann',
    lastName: 'Smith',
    email: 'ann.smith@nexabank.demo',
    phone: '+91 98765 43210',
    status: 'ACTIVE',
    daysAgo: 520,
  },
  {
    id: 2,
    number: 'CUST-100002',
    firstName: 'Rahul',
    lastName: 'Verma',
    email: 'rahul.verma@nexabank.demo',
    phone: '+91 98111 22334',
    status: 'ACTIVE',
    daysAgo: 480,
  },
  {
    id: 3,
    number: 'CUST-100003',
    firstName: 'Priya',
    lastName: 'Nair',
    email: 'priya.nair@nexabank.demo',
    phone: '+91 99222 33445',
    status: 'ACTIVE',
    daysAgo: 430,
  },
  {
    id: 4,
    number: 'CUST-100004',
    firstName: 'Daniel',
    lastName: 'Fernandes',
    email: 'daniel.fernandes@nexabank.demo',
    phone: '+91 97333 44556',
    status: 'ACTIVE',
    daysAgo: 300,
  },
  {
    id: 5,
    number: 'CUST-100005',
    firstName: 'Meera',
    lastName: 'Iyer',
    email: 'meera.iyer@nexabank.demo',
    phone: '+91 96444 55667',
    status: 'INACTIVE',
    daysAgo: 610,
  },
];

const ACCOUNT_SEED: Array<{
  id: number;
  number: string;
  customerId: number;
  type: AccountType;
  status: AccountStatus;
  daysAgo: number;
}> = [
  { id: 1, number: '100000000001', customerId: 1, type: 'SAVINGS', status: 'ACTIVE', daysAgo: 510 },
  { id: 2, number: '100000000002', customerId: 1, type: 'CHECKING', status: 'ACTIVE', daysAgo: 510 },
  { id: 3, number: '100000000003', customerId: 2, type: 'SAVINGS', status: 'ACTIVE', daysAgo: 470 },
  { id: 4, number: '100000000004', customerId: 2, type: 'CHECKING', status: 'FROZEN', daysAgo: 470 },
  { id: 5, number: '100000000005', customerId: 3, type: 'SAVINGS', status: 'ACTIVE', daysAgo: 420 },
  { id: 6, number: '100000000006', customerId: 3, type: 'CHECKING', status: 'ACTIVE', daysAgo: 420 },
  { id: 7, number: '100000000007', customerId: 4, type: 'SAVINGS', status: 'ACTIVE', daysAgo: 295 },
  { id: 8, number: '100000000008', customerId: 5, type: 'SAVINGS', status: 'ACTIVE', daysAgo: 600 },
];

const BENEFICIARY_SEED: Array<{
  id: number;
  customerId: number;
  accountId: number;
  nickname: string;
  status: BeneficiaryStatus;
  minutesAgo: number;
}> = [
  { id: 1, customerId: 1, accountId: 3, nickname: 'Rahul Verma — Business', status: 'ACTIVE', minutesAgo: 43_200 },
  { id: 2, customerId: 1, accountId: 5, nickname: 'Priya Nair — Rent share', status: 'ACTIVE', minutesAgo: 30_240 },
  // Created 13h20m ago, used 12h ago (80 min later) — within the 24h window, so
  // the travel-advance transfer below trips NEW_BENEFICIARY and is held.
  { id: 3, customerId: 1, accountId: 7, nickname: 'Daniel Fernandes — Travel', status: 'ACTIVE', minutesAgo: 800 },
  // Created 30m ago, used 2m ago (28 min later) — the urgent-wire BLOCK case.
  { id: 4, customerId: 1, accountId: 6, nickname: 'Priya Nair — New payee', status: 'ACTIVE', minutesAgo: 30 },
  { id: 5, customerId: 1, accountId: 8, nickname: 'Meera Iyer — Dormant payee', status: 'DISABLED', minutesAgo: 172_800 },
  // Created 6d ago, used 4d ago — long-established payee, so the 70k settlement approves.
  { id: 6, customerId: 2, accountId: 8, nickname: 'Meera Iyer — Dormant payee', status: 'ACTIVE', minutesAgo: 8_640 },
];

interface Attempt {
  accountId: number;
  type: EvaluableType;
  amount: number;
  description: string;
  minutesAgo: number;
  destinationAccountId?: number;
  beneficiaryId?: number;
  /** Alert state the demo wants this attempt's alert to end up in. */
  alertStatus?: FraudAlertStatus;
  alertReason?: string;
}

/**
 * The seeded activity timeline, oldest first. Minutes-ago values are chosen so
 * the six rules fire in a believable pattern rather than all at once.
 *
 * The five held attempts below are the whole point of the demo dataset — each
 * one is produced by the real engine, not typed in, and each exercises a
 * different rule combination:
 *
 * | attempt                                   | rules that fire                       | score | outcome         |
 * |-------------------------------------------|--------------------------------------|-------|-----------------|
 * | acct 6 deposit 90,000 @ 4d                | LARGE + ACCOUNT_ACTIVITY_SPIKE        | 45    | REVIEW  → FALSE_POSITIVE |
 * | acct 1 transfer 75,000 @ 12h              | LARGE + NEW_BENEFICIARY               | 40    | REVIEW  → RESOLVED     |
 * | acct 7 withdrawal 82,000 @ 28m            | LARGE + RAPID_WITHDRAWALS             | 45    | REVIEW  → UNDER_REVIEW |
 * | acct 1 withdrawal 90,000 @ 3m             | LARGE + RAPID_WITHDRAWALS             | 45    | REVIEW  → OPEN        |
 * | acct 2 transfer 250,000 @ 2m              | LARGE + RAPID_TRANSFERS + NEW_BENEFICIARY + ACCOUNT_ACTIVITY_SPIKE | 80 | BLOCK → OPEN |
 *
 * Everything else approves and settles, which is what builds the balances and
 * the recent-transaction baselines the rules depend on.
 */
const ATTEMPTS: Attempt[] = [
  { accountId: 1, type: 'DEPOSIT', amount: 250_000, description: 'Salary credit — NexaBank Corp', minutesAgo: 20_160 },
  { accountId: 2, type: 'DEPOSIT', amount: 400_000, description: 'Salary credit — NexaBank Corp', minutesAgo: 20_160 },
  { accountId: 1, type: 'WITHDRAWAL', amount: 5_000, description: 'ATM withdrawal', minutesAgo: 14_400 },
  { accountId: 3, type: 'DEPOSIT', amount: 480_000, description: 'Quarterly business credit', minutesAgo: 10_080 },
  { accountId: 6, type: 'DEPOSIT', amount: 5_000, description: 'Card settlement', minutesAgo: 10_000 },
  { accountId: 6, type: 'DEPOSIT', amount: 6_000, description: 'Card settlement', minutesAgo: 9_000 },
  { accountId: 6, type: 'DEPOSIT', amount: 5_500, description: 'Card settlement', minutesAgo: 8_000 },
  {
    accountId: 1,
    type: 'TRANSFER_DEBIT',
    amount: 25_000,
    description: 'Invoice payment',
    minutesAgo: 7_200,
    destinationAccountId: 3,
    beneficiaryId: 1,
  },
  { accountId: 1, type: 'WITHDRAWAL', amount: 12_000, description: 'Utility bills', minutesAgo: 6_000 },
  // Long-established payee, so only LARGE_TRANSACTION fires (25) → APPROVE.
  {
    accountId: 3,
    type: 'TRANSFER_DEBIT',
    amount: 70_000,
    description: 'Settlement to dormant payee',
    minutesAgo: 5_760,
    destinationAccountId: 8,
    beneficiaryId: 6,
  },
  {
    accountId: 6,
    type: 'DEPOSIT',
    amount: 90_000,
    description: 'Client remittance',
    minutesAgo: 5_760,
    alertStatus: 'FALSE_POSITIVE',
    alertReason: 'Regular client remittance, customer confirmed by phone.',
    // Account 6's three earlier 5-6k card settlements give it enough history for
    // ACCOUNT_ACTIVITY_SPIKE: 90k > 2 × 5.5k average = 11k. So LARGE (25) plus
    // the spike (20) = 45 → REVIEW, later dismissed as a false positive.
  },
  { accountId: 2, type: 'DEPOSIT', amount: 18_500.5, description: 'Card settlement', minutesAgo: 5_040 },
  {
    accountId: 2,
    type: 'TRANSFER_DEBIT',
    amount: 30_000,
    description: 'Vendor settlement',
    minutesAgo: 3_600,
    destinationAccountId: 5,
    beneficiaryId: 2,
  },
  { accountId: 5, type: 'DEPOSIT', amount: 132_750.75, description: 'Payroll credit', minutesAgo: 2_880 },
  { accountId: 5, type: 'WITHDRAWAL', amount: 22_500, description: 'Vendor payment', minutesAgo: 2_400 },
  { accountId: 4, type: 'DEPOSIT', amount: 25_000, description: 'Payroll credit', minutesAgo: 2_160 },
  { accountId: 5, type: 'WITHDRAWAL', amount: 8_000, description: 'Petty cash', minutesAgo: 1_800 },
  { accountId: 7, type: 'DEPOSIT', amount: 180_000, description: 'Opening deposit', minutesAgo: 1_500 },
  { accountId: 8, type: 'DEPOSIT', amount: 3_200, description: 'Savings top-up', minutesAgo: 1_200 },
  { accountId: 1, type: 'DEPOSIT', amount: 400_000, description: 'Insurance payout', minutesAgo: 1_440 },
  {
    accountId: 1,
    type: 'TRANSFER_DEBIT',
    amount: 75_000,
    description: 'Travel advance',
    minutesAgo: 720,
    destinationAccountId: 7,
    beneficiaryId: 3,
    alertStatus: 'RESOLVED',
    alertReason: 'Travel advance approved by branch manager.',
    // Payee 3 was created 800 minutes ago, so NEW_BENEFICIARY (15) fires on top
    // of LARGE_TRANSACTION (25) = 40 → REVIEW, since cleared by the analyst.
  },
  // Below the 2× recent average of ~54.4k, so ACCOUNT_ACTIVITY_SPIKE stays quiet.
  { accountId: 5, type: 'DEPOSIT', amount: 100_000, description: 'Client settlement', minutesAgo: 600 },
  // Three withdrawals 3-6 minutes before the flagged one, so RAPID_WITHDRAWALS
  // sees count >= 3 inside its 10-minute window.
  { accountId: 7, type: 'WITHDRAWAL', amount: 12_000, description: 'ATM withdrawal', minutesAgo: 36 },
  { accountId: 7, type: 'WITHDRAWAL', amount: 13_000, description: 'ATM withdrawal', minutesAgo: 33 },
  { accountId: 7, type: 'WITHDRAWAL', amount: 14_000, description: 'ATM withdrawal', minutesAgo: 30 },
  // Same shape on account 1: 6k/6.5k/7k build the history, then a 90k withdrawal
  // trips LARGE_TRANSACTION + RAPID_WITHDRAWALS = 45 (MEDIUM → REVIEW, OPEN).
  // The 90k line is annotated further down the list, after the two
  // UNDER_REVIEW/OPEN cases, so the ordering stays obvious.
  { accountId: 1, type: 'WITHDRAWAL', amount: 6_000, description: 'ATM withdrawal', minutesAgo: 9 },
  { accountId: 1, type: 'WITHDRAWAL', amount: 6_500, description: 'ATM withdrawal', minutesAgo: 7 },
  { accountId: 1, type: 'WITHDRAWAL', amount: 7_000, description: 'ATM withdrawal', minutesAgo: 5 },
  {
    accountId: 2,
    type: 'TRANSFER_DEBIT',
    amount: 5_000,
    description: 'Card payout',
    minutesAgo: 11,
    destinationAccountId: 3,
    beneficiaryId: 1,
  },
  {
    accountId: 2,
    type: 'TRANSFER_DEBIT',
    amount: 4_500,
    description: 'Card payout',
    minutesAgo: 8,
    destinationAccountId: 3,
    beneficiaryId: 1,
  },
  {
    accountId: 2,
    type: 'TRANSFER_DEBIT',
    amount: 4_000,
    description: 'Card payout',
    minutesAgo: 5,
    destinationAccountId: 3,
    beneficiaryId: 1,
  },
  {
    accountId: 7,
    type: 'WITHDRAWAL',
    amount: 82_000,
    description: 'ATM withdrawal',
    minutesAgo: 28,
    alertStatus: 'UNDER_REVIEW',
    // 4th withdrawal in the window after the 12k/13k/14k cluster:
    // LARGE_TRANSACTION (25) + RAPID_WITHDRAWALS (20) = 45 → REVIEW, and the
    // analyst has already picked it up.
  },
  {
    accountId: 1,
    type: 'WITHDRAWAL',
    amount: 90_000,
    description: 'ATM withdrawal',
    minutesAgo: 3,
    // 4th withdrawal inside the window (see the 6k/6.5k/7k cluster above):
    // LARGE_TRANSACTION (25) + RAPID_WITHDRAWALS (20) = 45 → REVIEW, left OPEN
    // so the analyst queue has a fresh untouched case.
  },
  {
    accountId: 2,
    type: 'TRANSFER_DEBIT',
    amount: 250_000,
    description: 'Urgent wire to newly added payee',
    minutesAgo: 2,
    destinationAccountId: 6,
    beneficiaryId: 4,
    // 4th outgoing transfer after the 5k/4.5k/4k card payouts, to a payee added
    // 28 minutes ago: LARGE (25) + RAPID_TRANSFERS (20) + NEW_BENEFICIARY (15)
    // + ACCOUNT_ACTIVITY_SPIKE (20) = 80 → CRITICAL, BLOCK.
  },
];

const MINUTE_MS = 60_000;
/** How many recent movements each rule may look at (backend bounded lookback). */
const RULE_BASELINE_LIMIT = 20;
const AUDIT_LIMIT = 200;

export function buildDemoDataset(engine: FraudRuleEngine, now: Date = new Date()): DemoDataset {
  const at = (minutesAgo: number): Date => new Date(now.getTime() - minutesAgo * MINUTE_MS);

  const customers: Customer[] = CUSTOMER_SEED.map((c) => ({
    id: c.id,
    customerNumber: c.number,
    firstName: c.firstName,
    lastName: c.lastName,
    email: c.email,
    phone: c.phone,
    status: c.status,
    createdAt: at(c.daysAgo * 24 * 60).toISOString(),
    updatedAt: at(c.daysAgo * 24 * 60).toISOString(),
  }));

  const accounts: Account[] = ACCOUNT_SEED.map((a) => ({
    id: a.id,
    accountNumber: a.number,
    customerId: a.customerId,
    customerNumber: customers.find((c) => c.id === a.customerId)?.customerNumber ?? '—',
    accountType: a.type,
    balance: 0,
    currency: 'INR',
    status: a.status,
    createdAt: at(a.daysAgo * 24 * 60).toISOString(),
    updatedAt: at(a.daysAgo * 24 * 60).toISOString(),
  }));

  const beneficiaries: Beneficiary[] = BENEFICIARY_SEED.map((b) => ({
    id: b.id,
    customerId: b.customerId,
    beneficiaryAccountId: b.accountId,
    beneficiaryAccountNumber:
      accounts.find((a) => a.id === b.accountId)?.accountNumber ?? '—',
    nickname: b.nickname,
    status: b.status,
    createdAt: at(b.minutesAgo).toISOString(),
    updatedAt: at(b.minutesAgo).toISOString(),
  }));

  const transactions: Transaction[] = [];
  const fraudEvaluations: FraudEvaluation[] = [];
  const fraudAlerts: FraudAlert[] = [];
  const auditLog: DemoActivityEntry[] = [];
  const takenReferences: string[] = [];

  let activityId = 0;
  const record = (
    type: DemoActivityType,
    when: Date,
    summary: string,
    reference: string | null = null,
    details: string | null = null,
  ): void => {
    activityId += 1;
    auditLog.push({ id: activityId, at: when.toISOString(), type, summary, reference, details });
  };

  // Oldest first: the running balance and the rule baselines both depend on it.
  const timeline = [...ATTEMPTS].sort((a, b) => b.minutesAgo - a.minutesAgo);

  for (const attempt of timeline) {
    const when = at(attempt.minutesAgo);
    const source = accounts.find((a) => a.id === attempt.accountId);
    if (!source || source.status !== 'ACTIVE') {
      continue;
    }
    const destination =
      attempt.destinationAccountId !== undefined
        ? accounts.find((a) => a.id === attempt.destinationAccountId)
        : undefined;
    if (attempt.destinationAccountId !== undefined && !destination) {
      continue;
    }

    const isDebit = attempt.type !== 'DEPOSIT';
    const balanceBefore = source.balance;
    const balanceAfter = round2(
      isDebit ? balanceBefore - attempt.amount : balanceBefore + attempt.amount,
    );
    if (isDebit && balanceAfter < 0) {
      // A seed entry that could never have settled — skip it rather than
      // inventing a negative balance.
      continue;
    }

    const beneficiary = beneficiaries.find((b) => b.id === attempt.beneficiaryId);
    const isTransfer = attempt.type === 'TRANSFER_DEBIT';

    // Bounded, newest-first baseline exactly like the backend evaluation context.
    const recentTransactions = transactions
      .filter((t) => t.accountId === source.id && t.status === 'COMPLETED')
      .slice(-RULE_BASELINE_LIMIT)
      .reverse();

    const outcome = engine.evaluate({
      accountId: source.id,
      accountNumber: source.accountNumber,
      amount: attempt.amount,
      type: attempt.type,
      beneficiaryId: beneficiary?.id ?? null,
      beneficiaryCreatedAt: beneficiary?.createdAt ?? null,
      recentTransactions: recentTransactions.map((t) => ({
        transactionType: t.transactionType,
        amount: t.amount,
        createdAt: t.createdAt,
      })),
      now: when,
    });

    const reference = nextReference(isTransfer ? 'TRF' : 'TXN', when, takenReferences);
    const evaluatedAt = when.toISOString();

    const evaluation: FraudEvaluation = {
      id: fraudEvaluations.length + 1,
      accountId: source.id,
      accountNumber: source.accountNumber,
      transactionId: null,
      attemptedReference: reference,
      transferReference: isTransfer ? reference : null,
      transactionType: attempt.type,
      amount: attempt.amount,
      currency: source.currency,
      riskScore: outcome.riskScore,
      riskLevel: outcome.riskLevel,
      decision: outcome.decision,
      factors: outcome.factors,
      evaluationVersion: engine.config.evaluationVersion,
      evaluationReason: engine.reasonFor(outcome),
      evaluatedBy: DEMO_OPERATOR,
      evaluatedAt,
    };
    fraudEvaluations.push(evaluation);

    if (outcome.decision !== 'APPROVE') {
      // Held: no ledger row, no balance movement — but the alert is raised.
      const status: FraudAlertStatus = attempt.alertStatus ?? 'OPEN';
      const reviewed = status !== 'OPEN';
      fraudAlerts.push({
        id: fraudAlerts.length + 1,
        severity: outcome.riskLevel as FraudAlertSeverity,
        status,
        reason: attempt.alertReason ?? engine.reasonFor(outcome),
        createdAt: evaluatedAt,
        reviewedAt: reviewed ? new Date(when.getTime() + 20 * MINUTE_MS).toISOString() : null,
        reviewedBy: reviewed ? DEMO_ANALYST : null,
        accountId: source.id,
        accountNumber: source.accountNumber,
        transactionId: null,
        evaluationId: evaluation.id,
        riskScore: outcome.riskScore,
        decision: outcome.decision,
        riskLevel: outcome.riskLevel,
        amount: attempt.amount,
        currency: source.currency,
        attemptedReference: reference,
        transferReference: isTransfer ? reference : null,
        evaluationVersion: engine.config.evaluationVersion,
        factors: outcome.factors,
      });
      record(
        'FRAUD_ALERT',
        when,
        `${outcome.decision} held ${source.accountNumber} ${attempt.type} ${attempt.amount} ${source.currency}`,
        reference,
        engine.reasonFor(outcome),
      );
      continue;
    }

    // Approved: write the ledger row(s) and move the balance.
    if (isTransfer && destination) {
      const destinationBefore = destination.balance;
      const destinationAfter = round2(destinationBefore + attempt.amount);
      const debit: Transaction = {
        transactionReference: `${reference}-D`,
        accountId: source.id,
        accountNumber: source.accountNumber,
        transactionType: 'TRANSFER_DEBIT' as TransactionType,
        amount: attempt.amount,
        currency: source.currency,
        balanceBefore,
        balanceAfter,
        status: 'COMPLETED',
        description: attempt.description,
        transferReference: reference,
        counterpartyAccountNumber: destination.accountNumber,
        createdAt: evaluatedAt,
        completedAt: evaluatedAt,
      };
      const credit: Transaction = {
        transactionReference: `${reference}-C`,
        accountId: destination.id,
        accountNumber: destination.accountNumber,
        transactionType: 'TRANSFER_CREDIT' as TransactionType,
        amount: attempt.amount,
        currency: source.currency,
        balanceBefore: destinationBefore,
        balanceAfter: destinationAfter,
        status: 'COMPLETED',
        description: attempt.description,
        transferReference: reference,
        counterpartyAccountNumber: source.accountNumber,
        createdAt: evaluatedAt,
        completedAt: evaluatedAt,
      };
      transactions.push(debit, credit);
      takenReferences.push(reference);
      source.balance = balanceAfter;
      source.updatedAt = evaluatedAt;
      destination.balance = destinationAfter;
      destination.updatedAt = evaluatedAt;
      record(
        'TRANSFER',
        when,
        `Transferred ${attempt.amount} ${source.currency} from ${source.accountNumber} to ${destination.accountNumber}`,
        reference,
        attempt.description,
      );
      continue;
    }

    const row: Transaction = {
      transactionReference: reference,
      accountId: source.id,
      accountNumber: source.accountNumber,
      transactionType: attempt.type === 'DEPOSIT' ? 'DEPOSIT' : 'WITHDRAWAL',
      amount: attempt.amount,
      currency: source.currency,
      balanceBefore,
      balanceAfter,
      status: 'COMPLETED',
      description: attempt.description,
      transferReference: null,
      counterpartyAccountNumber: null,
      createdAt: evaluatedAt,
      completedAt: evaluatedAt,
    };
    transactions.push(row);
    takenReferences.push(reference);
    source.balance = balanceAfter;
    source.updatedAt = evaluatedAt;
    record(
      attempt.type === 'DEPOSIT' ? 'DEPOSIT' : 'WITHDRAWAL',
      when,
      `${attempt.type === 'DEPOSIT' ? 'Deposited' : 'Withdrew'} ${attempt.amount} ${source.currency} on ${source.accountNumber}`,
      reference,
      attempt.description,
    );
  }

  record(
    'SYSTEM',
    now,
    `Demo dataset initialised: ${customers.length} customers, ${accounts.length} accounts, ${transactions.length} transactions, ${fraudAlerts.length} fraud alerts.`,
  );

  return {
    customers,
    accounts,
    transactions,
    beneficiaries,
    fraudAlerts,
    fraudEvaluations,
    // Newest first, bounded — the admin screen only ever shows recent activity.
    auditLog: auditLog
      .slice()
      .reverse()
      .slice(0, AUDIT_LIMIT)
      .map((entry, index) => ({ ...entry, id: index + 1 })),
    activeCustomerId: 1,
  };
}
