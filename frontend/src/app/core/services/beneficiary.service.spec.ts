import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { BeneficiaryService } from './beneficiary.service';
import { DemoRepositoryService } from './demo-repository.service';
import { LocalStorageService } from './local-storage.service';
import { Account } from '../../shared/models/account.model';
import { Customer } from '../../shared/models/customer.model';
import { Beneficiary } from '../../shared/models/beneficiary.model';

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

const OWN_ACCOUNT: Account = {
  id: 3,
  accountNumber: '100000000003',
  customerId: 1,
  customerNumber: 'CUST-100001',
  accountType: 'SAVINGS',
  balance: 0,
  currency: 'INR',
  status: 'ACTIVE',
  createdAt: '2026-09-18T00:00:00Z',
  updatedAt: '2026-09-18T00:00:00Z',
};

const BEN: Beneficiary = {
  id: 1,
  customerId: 1,
  beneficiaryAccountId: 9,
  beneficiaryAccountNumber: '100000000009',
  nickname: 'My Savings',
  status: 'ACTIVE',
  createdAt: '2026-09-18T10:00:00Z',
  updatedAt: '2026-09-18T10:00:00Z',
};

/** Was driven by `HttpTestingController` against `/api/v1/beneficiaries`. */
describe('BeneficiaryService', () => {
  let api: BeneficiaryService;
  let repo: DemoRepositoryService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    api = TestBed.inject(BeneficiaryService);
    repo = TestBed.inject(DemoRepositoryService);
    // jsdom keeps one `localStorage` per test file, so blank it to stay
    // independent of whatever the previous spec left behind.
    TestBed.inject(LocalStorageService).clearAll();
    repo.saveCustomers([OWNER]);
    repo.saveAccounts([OWN_ACCOUNT]);
    repo.saveBeneficiaries([BEN]);
    repo.setActiveCustomerId(OWNER.id);
  });

  it('lists only the acting customer’s beneficiaries', () => {
    let list: Beneficiary[] = [];
    api.list().subscribe((r) => (list = r));
    expect(list).toEqual([BEN]);

    repo.saveBeneficiaries([BEN, { ...BEN, id: 2, customerId: 99 }]);
    api.list().subscribe((r) => (list = r));
    expect(list).toHaveLength(1);
  });

  it('creates a beneficiary for one of the customer’s own accounts', () => {
    let created: Beneficiary | undefined;
    api.create(3, 'Payee').subscribe((b) => (created = b));

    expect(created?.nickname).toBe('Payee');
    expect(created?.beneficiaryAccountId).toBe(OWN_ACCOUNT.id);
    expect(created?.beneficiaryAccountNumber).toBe(OWN_ACCOUNT.accountNumber);
    expect(created?.customerId).toBe(OWNER.id);
    expect(created?.status).toBe('ACTIVE');
    expect(repo.beneficiaries()).toHaveLength(2);
  });

  it('falls back to the account number when the nickname is blank', () => {
    let created: Beneficiary | undefined;
    api.create(3, '   ').subscribe((b) => (created = b));
    expect(created?.nickname).toBe('Payee 100000000003');
  });

  it('trims a padded nickname instead of storing the whitespace', () => {
    let created: Beneficiary | undefined;
    api.create(3, '  Landlord  ').subscribe((b) => (created = b));
    expect(created?.nickname).toBe('Landlord');
  });

  it('rejects adding an account the customer does not own', () => {
    const someoneElses: Account = { ...OWN_ACCOUNT, id: 8, customerId: 77 };
    repo.saveAccounts([OWN_ACCOUNT, someoneElses]);

    let status = 0;
    api.create(8, 'Not mine').subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });

    expect(status).toBe(400);
    expect(repo.beneficiaries()).toHaveLength(1);
  });

  it('returns 404 when the target account does not exist', () => {
    let status = 0;
    api.create(999, 'Ghost').subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });

  it('disables softly, preserving the row and its history', () => {
    let updated: Beneficiary | undefined;
    api.disable(1).subscribe((b) => (updated = b));

    expect(updated?.status).toBe('DISABLED');
    expect(updated?.id).toBe(BEN.id);
    // Soft delete: the row stays so past transfers still resolve their payee.
    expect(repo.beneficiaries()).toHaveLength(1);
  });

  it('rejects disabling twice with 400', () => {
    api.disable(1).subscribe();
    let status = 0;
    api.disable(1).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(400);
  });

  it('returns 404 for an unknown beneficiary', () => {
    let status = 0;
    api.get(999).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });

  it('returns 404 when disabling an unknown beneficiary', () => {
    let status = 0;
    api.disable(999).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });
});
