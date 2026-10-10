import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Transaction,
  TransactionPage,
  newIdempotencyKey,
} from '../../shared/models/transaction.model';

/**
 * Transaction engine API client (Phase 4).
 *
 * Deposits and withdrawals are idempotent: each call carries a client-generated
 * idempotency key so a retried request cannot double-post. History is served
 * newest-first and paginated by the backend.
 */
@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Credits an account; the backend runs the fraud gate before settling. */
  deposit(accountId: number, amount: number, description?: string | null): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.base}/api/v1/accounts/${accountId}/deposits`, {
      amount,
      description: description ?? null,
      idempotencyKey: newIdempotencyKey(),
    });
  }

  /** Debits an account; insufficient funds or a fraud hold returns 422. */
  withdraw(
    accountId: number,
    amount: number,
    description?: string | null,
  ): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.base}/api/v1/accounts/${accountId}/withdrawals`, {
      amount,
      description: description ?? null,
      idempotencyKey: newIdempotencyKey(),
    });
  }

  /** Newest-first history for one account. */
  history(accountId: number, page = 0, size = 20): Observable<TransactionPage> {
    return this.http.get<TransactionPage>(
      `${this.base}/api/v1/accounts/${accountId}/transactions`,
      { params: this.pageParams(page, size) },
    );
  }

  /** History across the authenticated customer's own accounts (JWT-derived). */
  ownHistory(page = 0, size = 20): Observable<TransactionPage> {
    return this.http.get<TransactionPage>(`${this.base}/api/v1/accounts/me/transactions`, {
      params: this.pageParams(page, size),
    });
  }

  private pageParams(page: number, size: number): HttpParams {
    return new HttpParams().set('page', String(page)).set('size', String(size));
  }
}
