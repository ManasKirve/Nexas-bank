import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { AccountService } from './account.service';
import { DemoRepositoryService } from './demo-repository.service';
import { LocalStorageService } from './local-storage.service';
import { Customer } from '../../shared/models/customer.model';
import { Account } from '../../shared/models/account.model';

const OWNER: Customer = {
  id: 7,
  customerNumber: 'CUST-100007',
  firstName: 'Ann',
  lastName: 'Smith',
  email: 'ann@example.com',
  phone: null,
  status: 'ACTIVE',
  createdAt: '2026-09-18T00:00:00Z',
  updatedAt: '2026-09-18T00:00:00Z',
};

const ACCOUNT: Account = {
  id: 1,
  accountNumber: '100000000001',
  customerId: 7,
  customerNumber: 'CUST-100007',
  accountType: 'SAVINGS',
  balance: 0,
  currency: 'INR',
  status: 'ACTIVE',
  createdAt: '2026-09-18T00:00:00Z',
  updatedAt: '2026-09-18T00:00:00Z',
};

/**
 * These used to drive `HttpTestingController` against `/api/v1/accounts`. The
 * service now reads and writes LocalStorage through the repository, so each
 * test seeds the store and asserts on the returned projection.
 */
describe('AccountService', () => {
  let api: AccountService;
  let repo: DemoRepositoryService;
  let storage: LocalStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    api = TestBed.inject(AccountService);
    repo = TestBed.inject(DemoRepositoryService);
    storage = TestBed.inject(LocalStorageService);
    // jsdom keeps one `localStorage` per test file, so blank it to stay
    // independent of whatever the previous spec left behind.
    storage.clearAll();
    repo.saveCustomers([OWNER]);
    repo.saveAccounts([ACCOUNT]);
    repo.setActiveCustomerId(OWNER.id);
  });

  it('lists every account in the store', () => {
    let rows: Account[] = [];
    api.getAll().subscribe((r) => (rows = r));
    expect(rows).toEqual([ACCOUNT]);
  });

  it('getById returns 404 for an unknown account', () => {
    let status = 0;
    api.getById(999).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });

  it('getById returns the seeded account', () => {
    let found: Account | undefined;
    api.getById(1).subscribe((a) => (found = a));
    expect(found?.accountNumber).toBe(ACCOUNT.accountNumber);
  });

  it('filters accounts by customer', () => {
    let rows: Account[] = [];
    api.getByCustomer(7).subscribe((r) => (rows = r));
    expect(rows).toHaveLength(1);

    api.getByCustomer(999).subscribe((r) => (rows = r));
    expect(rows).toEqual([]);
  });

  it('getMyAccounts follows the acting demo customer', () => {
    let rows: Account[] = [];
    api.getMyAccounts().subscribe((r) => (rows = r));
    expect(rows).toHaveLength(1);

    repo.setActiveCustomerId(999);
    api.getMyAccounts().subscribe((r) => (rows = r));
    expect(rows).toEqual([]);
  });

  it('opens an account with a zero balance and the next number in sequence', () => {
    let created: Account | undefined;
    api.create({ customerId: 7, accountType: 'SAVINGS', currency: 'INR' }).subscribe((a) => (created = a));

    expect(created?.balance).toBe(0);
    expect(created?.accountNumber).toBe('100000000002');
    expect(created?.status).toBe('ACTIVE');
    expect(created?.customerNumber).toBe('CUST-100007');
    expect(repo.accounts()).toHaveLength(2);
  });

  it('rejects an unknown customer with 404', () => {
    let status = 0;
    api
      .create({ customerId: 42, accountType: 'SAVINGS', currency: 'INR' })
      .subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });

  it('rejects an unsupported currency with 422', () => {
    let status = 0;
    api
      .create({ customerId: 7, accountType: 'SAVINGS', currency: 'USD' })
      .subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(422);
  });

  it('changes status and persists it', () => {
    let updated: Account | undefined;
    api.changeStatus(1, 'FROZEN').subscribe((a) => (updated = a));
    expect(updated?.status).toBe('FROZEN');
    expect(repo.accounts()[0].status).toBe('FROZEN');
  });

  it('allows the legal ACTIVE → FROZEN → ACTIVE cycle', () => {
    api.changeStatus(1, 'FROZEN').subscribe();
    let reopened: Account | undefined;
    api.changeStatus(1, 'ACTIVE').subscribe((a) => (reopened = a));
    expect(reopened?.status).toBe('ACTIVE');
  });

  it('rejects an illegal status transition with 400', () => {
    api.changeStatus(1, 'CLOSED').subscribe();
    // CLOSED is terminal — reopening it must fail.
    let status = 0;
    api
      .changeStatus(1, 'ACTIVE')
      .subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(400);
    // The rejected change did not alter the stored status.
    expect(repo.accounts()[0].status).toBe('CLOSED');
  });

  it('rejects a status change on an unknown account with 404', () => {
    let status = 0;
    api
      .changeStatus(999, 'FROZEN')
      .subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });
});
