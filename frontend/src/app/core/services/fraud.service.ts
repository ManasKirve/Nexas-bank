import { Injectable, inject } from '@angular/core';
import { Observable, defer, of, throwError } from 'rxjs';
import {
  FraudAlert,
  FraudAlertPage,
  FraudAlertStatus,
  FraudDashboard,
  FraudEvaluation,
  FraudEvaluationPage,
} from '../../shared/models/fraud.model';
import { DemoRepositoryService } from './demo-repository.service';
import { DEMO_ANALYST } from '../data/demo-data';
import { ERRORS, byNewestFirst, paginate } from './demo-api';

const MAX_PAGE_SIZE = 200;

/** Legal alert transitions, identical to the backend state machine. */
const NEXT_STATUS: Record<FraudAlertStatus, FraudAlertStatus[]> = {
  OPEN: ['UNDER_REVIEW'],
  UNDER_REVIEW: ['RESOLVED', 'FALSE_POSITIVE'],
  RESOLVED: [],
  FALSE_POSITIVE: [],
};

/**
 * Local implementation of the Phase 6 fraud analyst API (`/api/v1/fraud/**`).
 *
 * The alert workflow is unchanged: OPEN → UNDER_REVIEW → RESOLVED or
 * FALSE_POSITIVE, and the illegal transitions still fail. Every decision comes
 * from the in-browser `FraudRuleEngine` recorded by the ledger pipeline.
 */
@Injectable({ providedIn: 'root' })
export class FraudService {
  private readonly repo = inject(DemoRepositoryService);

  /** Analyst review queue, optionally filtered by status. */
  alerts(status?: FraudAlertStatus | null, page = 0, size = 20): Observable<FraudAlertPage> {
    return defer(() => {
      const rows = this.repo
        .fraudAlerts()
        .filter((a) => !status || a.status === status)
        .sort(byNewestFirst<FraudAlert>('createdAt', (a) => a.id));
      return of(paginate(rows, page, size, MAX_PAGE_SIZE));
    });
  }

  /** One alert with its explainable evaluation context. */
  alert(id: number): Observable<FraudAlert> {
    return defer(() => {
      const found = this.repo.fraudAlerts().find((a) => a.id === id);
      return found ? of(found) : throwError(() => ERRORS.notFound('Fraud alert', id));
    });
  }

  /** OPEN → UNDER_REVIEW (analyst takes ownership). */
  review(id: number, reason?: string | null): Observable<FraudAlert> {
    return this.transition(id, 'UNDER_REVIEW', reason);
  }

  /** UNDER_REVIEW → RESOLVED (confirmed fraud / handled). */
  resolve(id: number, reason?: string | null): Observable<FraudAlert> {
    return this.transition(id, 'RESOLVED', reason);
  }

  /** UNDER_REVIEW → FALSE_POSITIVE (legitimate activity). */
  falsePositive(id: number, reason?: string | null): Observable<FraudAlert> {
    return this.transition(id, 'FALSE_POSITIVE', reason);
  }

  /** Historical evaluations (append-only, newest first). */
  evaluations(decision?: string | null, page = 0, size = 20): Observable<FraudEvaluationPage> {
    return defer(() => {
      const rows = this.repo
        .fraudEvaluations()
        .filter((e) => !decision || e.decision === decision)
        .sort(byNewestFirst<FraudEvaluation>('evaluatedAt', (e) => e.id));
      return of(paginate(rows, page, size, MAX_PAGE_SIZE));
    });
  }

  /** One historical evaluation. */
  evaluation(id: number): Observable<FraudEvaluation> {
    return defer(() => {
      const found = this.repo.fraudEvaluations().find((e) => e.id === id);
      return found ? of(found) : throwError(() => ERRORS.notFound('Fraud evaluation', id));
    });
  }

  /** Queue counts plus recent high-risk work for the dashboard. */
  dashboard(): Observable<FraudDashboard> {
    return defer(() => {
      const alerts = this.repo.fraudAlerts();
      const evaluations = this.repo.fraudEvaluations();
      const recent = alerts
        .slice()
        .sort(byNewestFirst<FraudAlert>('createdAt', (a) => a.id))
        .slice(0, 5);
      return of({
        openAlerts: alerts.filter((a) => a.status === 'OPEN').length,
        underReviewAlerts: alerts.filter((a) => a.status === 'UNDER_REVIEW').length,
        resolvedAlerts: alerts.filter((a) => a.status === 'RESOLVED').length,
        falsePositiveAlerts: alerts.filter((a) => a.status === 'FALSE_POSITIVE').length,
        highRiskAlerts: alerts.filter(
          (a) => a.riskLevel === 'HIGH' || a.riskLevel === 'CRITICAL',
        ).length,
        blockedEvaluations: evaluations.filter((e) => e.decision === 'BLOCK').length,
        reviewEvaluations: evaluations.filter((e) => e.decision === 'REVIEW').length,
        recentAlerts: recent,
      });
    });
  }

  private transition(
    id: number,
    status: FraudAlertStatus,
    reason?: string | null,
  ): Observable<FraudAlert> {
    return defer(() => {
      const rows = this.repo.fraudAlerts();
      const existing = rows.find((a) => a.id === id);
      if (!existing) {
        return throwError(() => ERRORS.notFound('Fraud alert', id));
      }
      if (!NEXT_STATUS[existing.status].includes(status)) {
        return throwError(
          () =>
            ERRORS.badState(
              `Alert #${id} cannot move from ${existing.status} to ${status}`,
            ),
        );
      }
      const note = reason?.trim() ? reason.trim() : existing.reason;
      const updated: FraudAlert = {
        ...existing,
        status,
        reason: note,
        reviewedAt: new Date().toISOString(),
        reviewedBy: DEMO_ANALYST,
      };
      this.repo.saveFraudAlerts(rows.map((a) => (a.id === id ? updated : a)));
      this.repo.appendActivity(
        'FRAUD_ALERT',
        `Alert #${id} moved to ${status}`,
        existing.attemptedReference,
        note,
      );
      return of(updated);
    });
  }
}
