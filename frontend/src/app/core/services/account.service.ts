import { Injectable, inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import {
  Account,
  AccountStatus,
  CreateAccountRequest,
} from '../../shared/models/account.model';
import { DemoRepositoryService } from './demo-repository.service';
import { ERRORS } from './demo-api';

const SUPPORTED_CURRENCY = 'INR';

/** Controlled status transitions — CLOSED is terminal, as in the backend. */
const NEXT_STATUS: Record<AccountStatus, AccountStatus[]> = {
  ACTIVE: ['FROZEN', 'CLOSED'],
  FROZEN: ['ACTIVE', 'CLOSED'],
  CLOSED: [],
};

/**
 * Accounts served from the local demo store, replacing `/api/v1/accounts`.
 */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly repo = inject(DemoRepositoryService);

  getAll(): Observable<Account[]> {
    return of(this.repo.accounts());
  }

  getById(id: number): Observable<Account> {
    const found = this.repo.accounts().find((a) => a.id === id);
    return found ? of(found) : throwError(() => ERRORS.notFound('Account', id));
  }

  /** Accounts of the demo customer currently selected in Admin. */
  getMyAccounts(): Observable<Account[]> {
    return this.getByCustomer(this.repo.activeCustomerId());
  }

  getByCustomer(customerId: number): Observable<Account[]> {
    return of(this.repo.accounts().filter((a) => a.customerId === customerId));
  }

  create(request: CreateAccountRequest): Observable<Account> {
    const customers = this.repo.customers();
    const owner = customers.find((c) => c.id === request.customerId);
    if (!owner) {
      return throwError(() => ERRORS.notFound('Customer', request.customerId));
    }
    if (request.currency !== SUPPORTED_CURRENCY) {
      return throwError(() => ERRORS.invalid('Unsupported account currency: ' + request.currency));
    }
    const rows = this.repo.accounts();
    const now = new Date().toISOString();
    const created: Account = {
      id: rows.reduce((max, a) => Math.max(max, a.id), 0) + 1,
      accountNumber: this.nextAccountNumber(rows),
      customerId: owner.id,
      customerNumber: owner.customerNumber,
      accountType: request.accountType,
      balance: 0,
      currency: SUPPORTED_CURRENCY,
      status: 'ACTIVE',
      createdAt: now,
      updatedAt: now,
    };
    this.repo.saveAccounts([...rows, created]);
    this.repo.appendActivity(
      'ACCOUNT',
      `Account ${created.accountNumber} opened for ${owner.customerNumber}`,
      null,
      created.accountType,
    );
    return of(created);
  }

  changeStatus(id: number, status: AccountStatus): Observable<Account> {
    const rows = this.repo.accounts();
    const existing = rows.find((a) => a.id === id);
    if (!existing) {
      return throwError(() => ERRORS.notFound('Account', id));
    }
    if (!NEXT_STATUS[existing.status].includes(status)) {
      return throwError(
        () =>
          ERRORS.badState(
            `Account ${existing.accountNumber} cannot move from ${existing.status} to ${status}`,
          ),
      );
    }
    const updated: Account = { ...existing, status, updatedAt: new Date().toISOString() };
    this.repo.saveAccounts(rows.map((a) => (a.id === id ? updated : a)));
    this.repo.appendActivity(
      'ACCOUNT',
      `Account ${updated.accountNumber} is now ${status}`,
      null,
      null,
    );
    return of(updated);
  }

  /** Mirrors the backend's `100000000001` sequence. */
  private nextAccountNumber(rows: Account[]): string {
    const highest = rows.reduce((max, a) => {
      const parsed = Number(a.accountNumber);
      return Number.isFinite(parsed) ? Math.max(max, parsed) : max;
    }, 100_000_000_000);
    return String(highest + 1);
  }
}
