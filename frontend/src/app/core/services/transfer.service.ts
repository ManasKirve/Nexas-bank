import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TransferResult } from '../../shared/models/beneficiary.model';
import { newIdempotencyKey } from '../../shared/models/transaction.model';

/**
 * Account-to-account transfer API client (`POST /api/v1/transfers`).
 *
 * Source ownership and beneficiary ownership are derived and enforced by the
 * backend from the JWT, never from the request body. The transfer is idempotent
 * via a client-generated key, and the backend runs the fraud gate before either
 * leg settles.
 */
@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  transfer(
    sourceAccountId: number,
    beneficiaryId: number,
    amount: number,
    description?: string | null,
  ): Observable<TransferResult> {
    return this.http.post<TransferResult>(`${this.base}/api/v1/transfers`, {
      sourceAccountId,
      beneficiaryId,
      amount,
      description: description ?? null,
      idempotencyKey: newIdempotencyKey(),
    });
  }
}
