import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { TransferService } from './transfer.service';
import { DemoRepositoryService } from './demo-repository.service';
import { LocalStorageService } from './local-storage.service';
import { Account } from '../../shared/models/account.model';
import { Customer } from '../../shared/models/customer.model';
import { Beneficiary, TransferResult } from '../../shared/models/beneficiary.model';

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

function account(id: number, balance: number): Account {
  return {
    id,
    accountNumber: `10000000000${id}`,
    customerId: OWNER.id,
    customerNumber: OWNER.customerNumber,
    accountType: 'SAVINGS',
    balance,
    currency: 'INR',
    status: 'ACTIVE',
    createdAt: '2026-09-18T00:00:00Z',
    updatedAt: '2026-09-18T00:00:00Z',
  };
}

const BENEFICIARY: Beneficiary = {
  id: 1,
  customerId: OWNER.id,
  beneficiaryAccountId: 4,
  beneficiaryAccountNumber: '100000000004',
  nickname: 'Payee',
  status: 'ACTIVE',
  // Well outside the 24h new-beneficiary window, so it does not trip the rule.
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

/** Was driven by `HttpTestingController` against `/api/v1/transfers`. */
describe('TransferService', () => {
  let api: TransferService;
  let repo: DemoRepositoryService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    api = TestBed.inject(TransferService);
    repo = TestBed.inject(DemoRepositoryService);
    // jsdom keeps one `localStorage` per test file, so blank it to stay
    // independent of whatever the previous spec left behind.
    TestBed.inject(LocalStorageService).clearAll();
    repo.saveCustomers([OWNER]);
    repo.saveAccounts([account(3, 1000), account(4, 200)]);
    repo.saveBeneficiaries([BENEFICIARY]);
    repo.saveTransactions([]);
    repo.saveFraudAlerts([]);
    repo.saveFraudEvaluations([]);
    repo.setActiveCustomerId(OWNER.id);
  });

  it('writes both legs under one shared transfer reference', () => {
    let result: TransferResult | undefined;
    api.transfer(3, 1, 250, 'Rent').subscribe((r) => (result = r));

    expect(result?.amount).toBe(250);
    expect(result?.sourceBalanceBefore).toBe(1000);
    expect(result?.sourceBalanceAfter).toBe(750);
    expect(result?.destinationBalanceBefore).toBe(200);
    expect(result?.destinationBalanceAfter).toBe(450);
    expect(result?.status).toBe('COMPLETED');

    const ledger = repo.transactions();
    expect(ledger).toHaveLength(2);
    expect(ledger[0].transactionType).toBe('TRANSFER_DEBIT');
    expect(ledger[1].transactionType).toBe('TRANSFER_CREDIT');
    // Both legs share the same reference, as the backend's double-entry did.
    expect(ledger[0].transferReference).toBe(result?.transferReference);
    expect(ledger[1].transferReference).toBe(result?.transferReference);
    expect(ledger[0].counterpartyAccountNumber).toBe('100000000004');

    expect(repo.accounts().map((a) => a.balance)).toEqual([750, 450]);
  });

  it('mints a new transfer reference per submission', () => {
    const seen = new Set<string>();
    for (let i = 0; i < 3; i++) {
      api.transfer(3, 1, 10 + i).subscribe((r) => seen.add(r.transferReference));
    }
    expect(seen.size).toBe(3);
  });

  it('insufficient funds fails with 422 and moves nothing', () => {
    let status = 0;
    api.transfer(3, 1, 99999).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });

    expect(status).toBe(422);
    expect(repo.accounts().map((a) => a.balance)).toEqual([1000, 200]);
    expect(repo.transactions()).toHaveLength(0);
  });

  it('rejects a disabled beneficiary with 400', () => {
    repo.saveBeneficiaries([{ ...BENEFICIARY, status: 'DISABLED' }]);
    let status = 0;
    api.transfer(3, 1, 100).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(400);
  });

  it('rejects an unknown beneficiary with 404', () => {
    let status = 0;
    api.transfer(3, 999, 100).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });

  it('rejects a transfer back into the same account', () => {
    repo.saveBeneficiaries([{ ...BENEFICIARY, beneficiaryAccountId: 3, beneficiaryAccountNumber: '100000000003' }]);
    let status = 0;
    api.transfer(3, 1, 100).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(422);
  });

  it('a small transfer to a payee added within 24h still approves', () => {
    // NEW_BENEFICIARY contributes 15, which is below the 30-point review
    // threshold, so on its own it does not hold a modest payment.
    const freshPayee: Beneficiary = {
      ...BENEFICIARY,
      createdAt: new Date(Date.now() - 60_000).toISOString(),
    };
    repo.saveBeneficiaries([freshPayee]);

    let result: TransferResult | undefined;
    api.transfer(3, 1, 100).subscribe((r) => (result = r));

    expect(result?.status).toBe('COMPLETED');
    expect(repo.transactions()).toHaveLength(2);
    expect(repo.fraudAlerts()).toHaveLength(0);
    expect(repo.fraudEvaluations()[0].riskScore).toBe(15);
  });

  it('a large transfer to a payee added within 24h is held, not settled', () => {
    const freshPayee: Beneficiary = {
      ...BENEFICIARY,
      createdAt: new Date(Date.now() - 60_000).toISOString(),
    };
    repo.saveBeneficiaries([freshPayee]);

    // 60,000 is over the large-transaction line (25) and the payee is brand new
    // (15), so 40 clears the review threshold.
    let status = 0;
    api.transfer(3, 1, 60_000).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });

    expect(status).toBe(422);
    expect(repo.transactions()).toHaveLength(0);
    expect(repo.accounts().map((a) => a.balance)).toEqual([1000, 200]);
    expect(repo.fraudAlerts()[0].decision).toBe('REVIEW');
    expect(repo.fraudAlerts()[0].factors.map((f) => f.code)).toEqual([
      'LARGE_TRANSACTION',
      'NEW_BENEFICIARY',
    ]);
  });
});
