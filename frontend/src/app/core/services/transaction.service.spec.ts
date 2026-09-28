import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { TransactionService } from './transaction.service';
import { DemoRepositoryService } from './demo-repository.service';
import { LocalStorageService } from './local-storage.service';
import { Account } from '../../shared/models/account.model';
import { Customer } from '../../shared/models/customer.model';
import { Transaction, TransactionPage } from '../../shared/models/transaction.model';

const OWNER: Customer = {
  id: 1,
  customerNumber: 'CUST-100001',
  firstName: 'Ann',
  lastName: 'Smith',
  email: 'ann@example.com',
  phone: null,
  status: 'ACTIVE',
  createdAt: '2026-09-18T00:00:00Z',
  updatedAt: '2026-09-18T00:00:00Z',
};

function account(id: number, balance: number, customerId = 1): Account {
  return {
    id,
    accountNumber: `10000000000${id}`,
    customerId,
    customerNumber: 'CUST-100001',
    accountType: 'SAVINGS',
    balance,
    currency: 'INR',
    status: 'ACTIVE',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T00:00:00Z',
  };
}

const TXN: Transaction = {
  transactionReference: 'TXN-20260918-000001',
  accountId: 3,
  accountNumber: '100000000003',
  transactionType: 'DEPOSIT',
  amount: 1000,
  currency: 'INR',
  balanceBefore: 0,
  balanceAfter: 1000,
  status: 'COMPLETED',
  description: 'Cash deposit',
  transferReference: null,
  counterpartyAccountNumber: null,
  createdAt: '2026-09-18T10:00:00Z',
  completedAt: '2026-09-18T10:00:01Z',
};

/**
 * The deposit/withdraw tests used to assert on the POST body and the idempotency
 * key. Locally there is no request, so these assert the effect that matters: the
 * ledger row, the balance movement, and the fraud gate holding a movement when
 * the engine says so.
 */
describe('TransactionService', () => {
  let api: TransactionService;
  let repo: DemoRepositoryService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    api = TestBed.inject(TransactionService);
    repo = TestBed.inject(DemoRepositoryService);
    // jsdom keeps one `localStorage` per test file, so blank it to stay
    // independent of whatever the previous spec left behind.
    TestBed.inject(LocalStorageService).clearAll();
    repo.saveCustomers([OWNER]);
    repo.saveAccounts([account(3, 0)]);
    repo.saveTransactions([]);
    repo.saveFraudAlerts([]);
    repo.saveFraudEvaluations([]);
    repo.setActiveCustomerId(OWNER.id);
  });

  it('deposit writes a ledger row and credits the balance', () => {
    let received: Transaction | undefined;
    api.deposit(3, 1000, 'Cash deposit').subscribe((t) => (received = t));

    expect(received?.transactionType).toBe('DEPOSIT');
    expect(received?.amount).toBe(1000);
    expect(received?.balanceBefore).toBe(0);
    expect(received?.balanceAfter).toBe(1000);
    expect(received?.description).toBe('Cash deposit');
    expect(received?.status).toBe('COMPLETED');
    expect(repo.accounts()[0].balance).toBe(1000);
    expect(repo.transactions()).toHaveLength(1);
  });

  it('deposit mints a unique reference per call', () => {
    const seen = new Set<string>();
    for (let i = 0; i < 3; i++) {
      api.deposit(3, 10 + i).subscribe((t) => seen.add(t.transactionReference));
    }
    expect(seen.size).toBe(3);
  });

  it('withdraw debits the balance', () => {
    repo.saveAccounts([account(3, 1000)]);
    let received: Transaction | undefined;
    api.withdraw(3, 400, 'ATM withdrawal').subscribe((t) => (received = t));

    expect(received?.transactionType).toBe('WITHDRAWAL');
    expect(received?.balanceBefore).toBe(1000);
    expect(received?.balanceAfter).toBe(600);
    expect(repo.accounts()[0].balance).toBe(600);
  });

  it('withdraw beyond the balance fails with 422 and moves nothing', () => {
    repo.saveAccounts([account(3, 100)]);
    let status = 0;
    api.withdraw(3, 99999).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });

    expect(status).toBe(422);
    expect(repo.accounts()[0].balance).toBe(100);
    expect(repo.transactions()).toHaveLength(0);
  });

  it('rejects a non-positive or over-precise amount with 422', () => {
    let status = 0;
    api.deposit(3, 0).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(422);

    api.deposit(3, 10.005).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(422);
  });

  it('a single large amount scores 25 and still approves', () => {
    // LARGE_TRANSACTION alone contributes 25, which is below the 30 review
    // threshold — one big deposit is not enough to trip the gate.
    let received: Transaction | undefined;
    api.deposit(3, 60_000).subscribe((t) => (received = t));

    expect(received?.status).toBe('COMPLETED');
    expect(repo.accounts()[0].balance).toBe(60_000);
    expect(repo.fraudEvaluations()[0].decision).toBe('APPROVE');
    expect(repo.fraudEvaluations()[0].riskScore).toBe(25);
    expect(repo.fraudAlerts()).toHaveLength(0);
  });

  it('a fourth withdrawal inside the rapid-withdrawal window is held', () => {
    repo.saveAccounts([account(3, 1_000_000)]);

    // Three settled withdrawals at 45,000: under the 50,000 large-transaction
    // line, and the baseline they build keeps the next amount from looking like
    // an activity spike (45,000 average × 2 = 90,000 > 60,000).
    for (let i = 0; i < 3; i++) {
      api.withdraw(3, 45_000).subscribe();
    }
    expect(repo.transactions()).toHaveLength(3);
    expect(repo.accounts()[0].balance).toBe(865_000);

    // The next attempt is over 50,000 (LARGE = 25) and it is the fourth
    // withdrawal inside the 10-minute window (RAPID_WITHDRAWALS = 20), so the
    // total of 45 crosses the 30-point review threshold.
    let status = 0;
    let message = '';
    api.withdraw(3, 60_000).subscribe({
      next: () => expect.unreachable(),
      error: (e) => {
        status = e.status;
        message = (e.error as { message: string }).message;
      },
    });

    expect(status).toBe(422);
    expect(message).toContain('REVIEW');
    // The whole point of the gate: the money did not move.
    expect(repo.transactions()).toHaveLength(3);
    expect(repo.accounts()[0].balance).toBe(865_000);
    expect(repo.fraudAlerts()).toHaveLength(1);
    expect(repo.fraudAlerts()[0].decision).toBe('REVIEW');
    expect(repo.fraudAlerts()[0].riskScore).toBe(45);
    expect(repo.fraudAlerts()[0].factors.map((f) => f.code)).toEqual([
      'LARGE_TRANSACTION',
      'RAPID_WITHDRAWALS',
    ]);
  });

  it('records an APPROVE evaluation too, so the decision history is complete', () => {
    api.deposit(3, 100).subscribe();
    expect(repo.fraudEvaluations()).toHaveLength(1);
    expect(repo.fraudEvaluations()[0].decision).toBe('APPROVE');
  });

  it('refuses to move money on a FROZEN account', () => {
    repo.saveAccounts([{ ...account(3, 1000), status: 'FROZEN' }]);
    let status = 0;
    api.deposit(3, 100).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(400);
  });

  it('history pages newest-first for one account', () => {
    repo.saveTransactions([TXN]);
    let page: TransactionPage | undefined;
    api.history(3, 0, 10).subscribe((p) => (page = p));

    expect(page?.totalElements).toBe(1);
    expect(page?.content[0].transactionReference).toBe(TXN.transactionReference);
  });

  it('history returns 404 for an unknown account', () => {
    let status = 0;
    api.history(999).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });

  it('ownHistory covers only the acting customer accounts', () => {
    repo.saveAccounts([account(3, 0, 1), account(4, 0, 2)]);
    repo.saveTransactions([
      TXN,
      { ...TXN, accountId: 4, accountNumber: '100000000004', transactionReference: 'TXN-OTHER' },
    ]);

    let page: TransactionPage | undefined;
    api.ownHistory(0, 50).subscribe((p) => (page = p));

    expect(page?.totalElements).toBe(1);
    expect(page?.content[0].accountId).toBe(3);
  });
});
