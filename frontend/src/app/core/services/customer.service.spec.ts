import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { CustomerService } from './customer.service';
import { DemoRepositoryService } from './demo-repository.service';
import { LocalStorageService } from './local-storage.service';
import { Customer } from '../../shared/models/customer.model';

const CUSTOMER: Customer = {
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

/** Was driven by `HttpTestingController` against `/api/v1/customers`. */
describe('CustomerService', () => {
  let api: CustomerService;
  let repo: DemoRepositoryService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    api = TestBed.inject(CustomerService);
    repo = TestBed.inject(DemoRepositoryService);
    // jsdom keeps one `localStorage` per test file, so blank it to stay
    // independent of whatever the previous spec left behind.
    TestBed.inject(LocalStorageService).clearAll();
    repo.saveCustomers([CUSTOMER]);
  });

  it('lists customers from the local store', () => {
    let rows: Customer[] = [];
    api.getAll().subscribe((r) => (rows = r));
    expect(rows).toEqual([CUSTOMER]);
  });

  it('creates a customer and assigns the next number in sequence', () => {
    let created: Customer | undefined;
    api
      .create({ firstName: 'Bob', lastName: 'Jones', email: 'bob@example.com' })
      .subscribe((c) => (created = c));

    expect(created?.customerNumber).toBe('CUST-100002');
    expect(created?.status).toBe('ACTIVE');
    expect(created?.phone).toBeNull();
    expect(repo.customers()).toHaveLength(2);
  });

  it('rejects a duplicate email with 409', () => {
    let status = 0;
    api
      .create({ firstName: 'Other', lastName: 'Person', email: 'ANN@example.com' })
      .subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(409);
  });

  it('rejects an invalid email with 400 and a field error map', () => {
    let fields: Record<string, string> | undefined;
    api
      .create({ firstName: 'Bad', lastName: 'Email', email: 'not-an-email' })
      .subscribe({
        next: () => expect.unreachable(),
        error: (e) => (fields = (e.error as { errors?: Record<string, string> }).errors),
      });
    expect(fields?.['email']).toBeTruthy();
  });

  it('deactivates softly, keeping the row', () => {
    let updated: Customer | undefined;
    api.deactivate(1).subscribe((c) => (updated = c));

    expect(updated?.status).toBe('INACTIVE');
    expect(repo.customers()).toHaveLength(1);
  });

  it('getMyProfile resolves the acting demo customer', () => {
    repo.setActiveCustomerId(1);
    let profile: Customer | undefined;
    api.getMyProfile().subscribe((c) => (profile = c));
    expect(profile?.id).toBe(1);
  });

  it('returns 404 for an unknown customer', () => {
    let status = 0;
    api
      .getById(999)
      .subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });
});
