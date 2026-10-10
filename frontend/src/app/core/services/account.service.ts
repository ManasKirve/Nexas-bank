import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Account,
  AccountStatus,
  CreateAccountRequest,
} from '../../shared/models/account.model';

/**
 * Accounts API client (`/api/v1/accounts`).
 *
 * Ownership-aware reads are served by the backend: `/accounts/me` derives the
 * customer from the JWT and `/customers/{id}/accounts` enforces access rules.
 * Balances are never sent by the client — new accounts always start at zero.
 */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  getAll(): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.base}/api/v1/accounts`);
  }

  getById(id: number): Observable<Account> {
    return this.http.get<Account>(`${this.base}/api/v1/accounts/${id}`);
  }

  /** Accounts of the authenticated customer (JWT-derived ownership). */
  getMyAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.base}/api/v1/accounts/me`);
  }

  getByCustomer(customerId: number): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.base}/api/v1/customers/${customerId}/accounts`);
  }

  create(request: CreateAccountRequest): Observable<Account> {
    return this.http.post<Account>(`${this.base}/api/v1/accounts`, request);
  }

  changeStatus(id: number, status: AccountStatus): Observable<Account> {
    return this.http.put<Account>(`${this.base}/api/v1/accounts/${id}/status`, { status });
  }
}
