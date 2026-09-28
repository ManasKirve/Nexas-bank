import { Injectable, inject } from '@angular/core';
import { Observable, defer, of, throwError } from 'rxjs';
import {
  Transaction,
  TransactionPage,
} from '../../shared/models/transaction.model';
import { Account } from '../../shared/models/account.model';
import { DemoRepositoryService } from './demo-repository.service';
import { LedgerService } from './ledger.service';
import { ERRORS, byNewestFirst, isValidAmount, paginate, round2 } from './demo-api';

const MAX_PAGE_SIZE = 200;

/**
 * Local implementation of the Phase 4 transaction engine: deposits,
 * withdrawals and paginated history.
 *
 * `defer` keeps the local store's synchronous work lazy and makes the
 * `HttpErrorResponse`s thrown by the ledger pipeline surface through the
 * observable error channel, exactly like the HTTP client did.
 */
@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly repo = inject(DemoRepositoryService);
  private readonly ledger = inject(LedgerService);

  /** Credits an account after the fraud engine approves the attempt. */
  deposit(accountId: number, amount: number, description?: string | null): Observable<Transaction> {
    return defer(() =>
      of(
        this.ledger.attempt({
          account: this.prepare(accountId, amount),
          amount,
          type: 'DEPOSIT',
          settle: (reference) => this.writeMovement(accountId, 'DEPOSIT', amount, description, reference),
        }),
      ),
    );
  }

  /** Debits an account; insufficient funds or a fraud hold fails with 422. */
  withdraw(
    accountId: number,
    amount: number,
    description?: string | null,
  ): Observable<Transaction> {
    return defer(() =>
      of(
        this.ledger.attempt({
          account: this.prepare(accountId, amount),
          amount,
          type: 'WITHDRAWAL',
          settle: (reference) =>
            this.writeMovement(accountId, 'WITHDRAWAL', amount, description, reference),
        }),
      ),
    );
  }

  /** Newest-first history for one account. */
  history(accountId: number, page = 0, size = 20): Observable<TransactionPage> {
    return defer(() => {
      if (!this.repo.accounts().some((a) => a.id === accountId)) {
        throw ERRORS.notFound('Account', accountId);
      }
      const rows = this.repo
        .transactions()
        .filter((t) => t.accountId === accountId)
        .sort(byNewestFirst<Transaction>('createdAt', (t) => t.transactionReference));
      return of(paginate(rows, page, size, MAX_PAGE_SIZE));
    });
  }

  /**
   * History across the demo customer's own accounts. The backend derived the
   * customer from the JWT; locally it is the acting customer chosen in Admin.
   */
  ownHistory(page = 0, size = 20): Observable<TransactionPage> {
    return defer(() => {
      const customerId = this.repo.activeCustomerId();
      const accountIds = new Set(
        this.repo
          .accounts()
          .filter((a) => a.customerId === customerId)
          .map((a) => a.id),
      );
      const rows = this.repo
        .transactions()
        .filter((t) => accountIds.has(t.accountId))
        .sort(byNewestFirst<Transaction>('createdAt', (t) => t.transactionReference));
      return of(paginate(rows, page, size, MAX_PAGE_SIZE));
    });
  }

  /** Validates the account and amount before the engine sees them. */
  private prepare(accountId: number, amount: number): Account {
    if (!isValidAmount(amount)) {
      throw ERRORS.invalid(`Amount must be a positive value with at most 2 decimals, got ${amount}`);
    }
    return this.ledger.requireActiveAccount(accountId);
  }

  /** Single-account movement: updates the balance and appends one ledger row. */
  private writeMovement(
    accountId: number,
    type: 'DEPOSIT' | 'WITHDRAWAL',
    amount: number,
    description: string | null | undefined,
    reference: string,
  ): Transaction {
    const accounts = this.repo.accounts();
    const account = accounts.find((a) => a.id === accountId);
    if (!account) {
      throw ERRORS.notFound('Account', accountId);
    }
    if (type === 'WITHDRAWAL') {
      this.ledger.requireFunds(account, amount);
    }
    const before = account.balance;
    const after = round2(type === 'DEPOSIT' ? before + amount : before - amount);
    const now = new Date().toISOString();

    this.repo.saveAccounts(
      accounts.map((a) => (a.id === accountId ? { ...a, balance: after, updatedAt: now } : a)),
    );
    const row: Transaction = {
      transactionReference: reference,
      accountId,
      accountNumber: account.accountNumber,
      transactionType: type,
      amount,
      currency: account.currency,
      balanceBefore: before,
      balanceAfter: after,
      status: 'COMPLETED',
      description: description?.trim() ? description.trim() : null,
      transferReference: null,
      counterpartyAccountNumber: null,
      createdAt: now,
      completedAt: now,
    };
    this.repo.saveTransactions([...this.repo.transactions(), row]);
    return row;
  }
}
