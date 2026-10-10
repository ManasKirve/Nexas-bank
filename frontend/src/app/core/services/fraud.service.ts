import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  FraudAlert,
  FraudAlertPage,
  FraudAlertStatus,
  FraudDashboard,
  FraudEvaluation,
  FraudEvaluationPage,
} from '../../shared/models/fraud.model';
import { Role } from '../../shared/models/auth.model';

/**
 * Fraud analyst API client (`/api/v1/fraud/**`). FRAUD_ANALYST and ADMIN only.
 *
 * Alert workflow transitions (review → resolve / false-positive) are enforced
 * by the backend state machine; illegal transitions return 400.
 */
@Injectable({ providedIn: 'root' })
export class FraudService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Analyst review queue, optionally filtered by status. */
  alerts(status?: FraudAlertStatus | null, page = 0, size = 20): Observable<FraudAlertPage> {
    let params = this.pageParams(page, size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<FraudAlertPage>(`${this.base}/api/v1/fraud/alerts`, { params });
  }

  alert(id: number): Observable<FraudAlert> {
    return this.http.get<FraudAlert>(`${this.base}/api/v1/fraud/alerts/${id}`);
  }

  /** OPEN → UNDER_REVIEW (analyst takes ownership). */
  review(id: number, reason?: string | null): Observable<FraudAlert> {
    return this.http.post<FraudAlert>(`${this.base}/api/v1/fraud/alerts/${id}/review`, {
      reason: reason ?? null,
    });
  }

  /** UNDER_REVIEW → RESOLVED (confirmed fraud / handled). */
  resolve(id: number, reason?: string | null): Observable<FraudAlert> {
    return this.http.post<FraudAlert>(`${this.base}/api/v1/fraud/alerts/${id}/resolve`, {
      reason: reason ?? null,
    });
  }

  /** UNDER_REVIEW → FALSE_POSITIVE (legitimate activity). */
  falsePositive(id: number, reason?: string | null): Observable<FraudAlert> {
    return this.http.post<FraudAlert>(`${this.base}/api/v1/fraud/alerts/${id}/false-positive`, {
      reason: reason ?? null,
    });
  }

  /** Historical evaluations (append-only, newest first). */
  evaluations(
    decision?: string | null,
    page = 0,
    size = 20,
  ): Observable<FraudEvaluationPage> {
    let params = this.pageParams(page, size);
    if (decision) {
      params = params.set('decision', decision);
    }
    return this.http.get<FraudEvaluationPage>(`${this.base}/api/v1/fraud/evaluations`, { params });
  }

  evaluation(id: number): Observable<FraudEvaluation> {
    return this.http.get<FraudEvaluation>(`${this.base}/api/v1/fraud/evaluations/${id}`);
  }

  /** Queue counts plus recent high-risk work for the dashboard. */
  dashboard(): Observable<FraudDashboard> {
    return this.http.get<FraudDashboard>(`${this.base}/api/v1/fraud/dashboard`);
  }

  private pageParams(page: number, size: number): HttpParams {
    return new HttpParams().set('page', String(page)).set('size', String(size));
  }
}

/** Roles allowed into the fraud console; kept for callers that gate UI. */
export const FRAUD_ACCESS_ROLES: Role[] = ['FRAUD_ANALYST', 'ADMIN'];
