import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { FraudService } from './fraud.service';
import { DemoRepositoryService } from './demo-repository.service';
import { LocalStorageService } from './local-storage.service';
import {
  FraudAlert,
  FraudAlertPage,
  FraudDashboard,
  FraudEvaluation,
  factorScore,
} from '../../shared/models/fraud.model';

const ALERT: FraudAlert = {
  id: 7,
  severity: 'HIGH',
  status: 'OPEN',
  reason: 'Decision REVIEW with score 40: LARGE_TRANSACTION, NEW_BENEFICIARY',
  createdAt: '2026-09-19T10:00:00Z',
  reviewedAt: null,
  reviewedBy: null,
  accountId: 3,
  accountNumber: '100000000001',
  transactionId: null,
  evaluationId: 11,
  riskScore: 40,
  decision: 'REVIEW',
  riskLevel: 'MEDIUM',
  amount: 60000,
  currency: 'INR',
  attemptedReference: 'TRF-20260919-000001',
  transferReference: 'TRF-20260919-000001',
  evaluationVersion: 'v1',
  factors: [
    { code: 'LARGE_TRANSACTION', description: 'Transaction exceeded configured threshold', score: 25 },
    { code: 'NEW_BENEFICIARY', description: 'Beneficiary was recently created', score: 15 },
  ],
};

const EVALUATION: FraudEvaluation = {
  id: 11,
  accountId: 3,
  accountNumber: '100000000001',
  transactionId: null,
  attemptedReference: 'TRF-20260919-000001',
  transferReference: 'TRF-20260919-000001',
  transactionType: 'TRANSFER_DEBIT',
  amount: 60000,
  currency: 'INR',
  riskScore: 40,
  riskLevel: 'MEDIUM',
  decision: 'REVIEW',
  factors: ALERT.factors,
  evaluationVersion: 'v1',
  evaluationReason: ALERT.reason,
  evaluatedBy: 'demo.analyst@nexabank.demo',
  evaluatedAt: '2026-09-19T10:00:00Z',
};

/** Was driven by `HttpTestingController` against `/api/v1/fraud/**`. */
describe('FraudService', () => {
  let api: FraudService;
  let repo: DemoRepositoryService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    api = TestBed.inject(FraudService);
    repo = TestBed.inject(DemoRepositoryService);
    // jsdom keeps one `localStorage` per test file, so blank it to stay
    // independent of whatever the previous spec left behind.
    TestBed.inject(LocalStorageService).clearAll();
    repo.saveFraudAlerts([ALERT]);
    repo.saveFraudEvaluations([EVALUATION]);
  });

  it('lists alerts with a status filter and paging', () => {
    let page: FraudAlertPage | undefined;
    api.alerts('OPEN', 0, 20).subscribe((p) => (page = p));

    expect(page?.totalElements).toBe(1);
    expect(page?.content[0].riskScore).toBe(40);
    expect(page?.content[0].factors.map((f) => f.code)).toEqual([
      'LARGE_TRANSACTION',
      'NEW_BENEFICIARY',
    ]);
  });

  it('drops alerts that do not match the status filter', () => {
    let page: FraudAlertPage | undefined;
    api.alerts('RESOLVED').subscribe((p) => (page = p));
    expect(page?.totalElements).toBe(0);
  });

  it('fetches one alert with its explainable factors', () => {
    let received: FraudAlert | undefined;
    api.alert(7).subscribe((a) => (received = a));

    expect(received?.evaluationVersion).toBe('v1');
    expect(received?.factors).toHaveLength(2);
  });

  it('returns 404 for an unknown alert', () => {
    let status = 0;
    api.alert(999).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });

  it('moves OPEN → UNDER_REVIEW and stamps the analyst', () => {
    let received: FraudAlert | undefined;
    api.review(7, 'taking a look').subscribe((a) => (received = a));

    expect(received?.status).toBe('UNDER_REVIEW');
    expect(received?.reviewedBy).toBe('demo.analyst@nexabank.demo');
    expect(received?.reviewedAt).toBeTruthy();
  });

  it('moves UNDER_REVIEW → RESOLVED with a reason', () => {
    api.review(7).subscribe();
    let received: FraudAlert | undefined;
    api.resolve(7, 'confirmed fraud').subscribe((a) => (received = a));

    expect(received?.status).toBe('RESOLVED');
    expect(received?.reason).toBe('confirmed fraud');
  });

  it('moves UNDER_REVIEW → FALSE_POSITIVE', () => {
    api.review(7).subscribe();
    let received: FraudAlert | undefined;
    api.falsePositive(7, 'legit').subscribe((a) => (received = a));
    expect(received?.status).toBe('FALSE_POSITIVE');
  });

  it('rejects an illegal transition with 400', () => {
    // OPEN cannot go straight to RESOLVED.
    let status = 0;
    api.resolve(7).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(400);
    expect(repo.fraudAlerts()[0].status).toBe('OPEN');
  });

  it('rejects a terminal status being changed again', () => {
    api.review(7).subscribe();
    api.resolve(7).subscribe();
    let status = 0;
    api.review(7).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(400);
  });

  it('rejects transitioning an alert that does not exist', () => {
    let status = 0;
    api.review(999).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });

  it('keeps the original reason when no note is supplied', () => {
    let received: FraudAlert | undefined;
    api.review(7).subscribe((a) => (received = a));
    expect(received?.reason).toBe(ALERT.reason);
  });

  it('lists historical evaluations with a decision filter', () => {
    let total = 0;
    api.evaluations('REVIEW').subscribe((p) => (total = p.totalElements));
    expect(total).toBe(1);

    api.evaluations('APPROVE').subscribe((p) => (total = p.totalElements));
    expect(total).toBe(0);
  });

  it('fetches one evaluation by id and 404s an unknown one', () => {
    let found: FraudEvaluation | undefined;
    api.evaluation(11).subscribe((e) => (found = e));
    expect(found?.decision).toBe('REVIEW');
    expect(found?.factors).toHaveLength(2);
    // factorScore tolerates either serialisation of the contribution.
    expect(found?.factors.map(factorScore)).toEqual([25, 15]);

    let status = 0;
    api.evaluation(999).subscribe({ next: () => expect.unreachable(), error: (e) => (status = e.status) });
    expect(status).toBe(404);
  });

  it('pages the alert queue with a size cap', () => {
    repo.saveFraudAlerts([ALERT, { ...ALERT, id: 8 }, { ...ALERT, id: 9 }]);

    let page: FraudAlertPage | undefined;
    api.alerts('OPEN', 0, 2).subscribe((p) => (page = p));
    expect(page?.content).toHaveLength(2);
    expect(page?.totalElements).toBe(3);
    expect(page?.totalPages).toBe(2);
  });

  it('aggregates the dashboard from the stored alerts and evaluations', () => {
    repo.saveFraudAlerts([
      ALERT,
      { ...ALERT, id: 8, status: 'UNDER_REVIEW', riskLevel: 'CRITICAL' },
      { ...ALERT, id: 9, status: 'RESOLVED' },
      { ...ALERT, id: 10, status: 'FALSE_POSITIVE', riskLevel: 'LOW' },
    ]);
    repo.saveFraudEvaluations([EVALUATION, { ...EVALUATION, id: 12, decision: 'BLOCK' }]);

    let received: FraudDashboard | undefined;
    api.dashboard().subscribe((d) => (received = d));

    expect(received?.openAlerts).toBe(1);
    expect(received?.underReviewAlerts).toBe(1);
    expect(received?.resolvedAlerts).toBe(1);
    expect(received?.falsePositiveAlerts).toBe(1);
    expect(received?.highRiskAlerts).toBe(1);
    expect(received?.reviewEvaluations).toBe(1);
    expect(received?.blockedEvaluations).toBe(1);
    expect(received?.recentAlerts).toHaveLength(4);
  });
});
