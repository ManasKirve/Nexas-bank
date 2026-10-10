import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Beneficiary } from '../../shared/models/beneficiary.model';

/**
 * Beneficiary API client (`/api/v1/beneficiaries`).
 *
 * Ownership is derived from the authenticated user by the backend. CUSTOMER,
 * BANK_EMPLOYEE and ADMIN may use beneficiaries; FRAUD_ANALYST is denied.
 * Deletion is a soft disable that preserves transfer history.
 */
@Injectable({ providedIn: 'root' })
export class BeneficiaryService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(): Observable<Beneficiary[]> {
    return this.http.get<Beneficiary[]>(`${this.base}/api/v1/beneficiaries`);
  }

  get(id: number): Observable<Beneficiary> {
    return this.http.get<Beneficiary>(`${this.base}/api/v1/beneficiaries/${id}`);
  }

  create(accountId: number, nickname?: string | null): Observable<Beneficiary> {
    return this.http.post<Beneficiary>(`${this.base}/api/v1/beneficiaries`, {
      accountId,
      nickname: nickname ?? null,
    });
  }

  disable(id: number): Observable<Beneficiary> {
    return this.http.delete<Beneficiary>(`${this.base}/api/v1/beneficiaries/${id}`);
  }
}
