import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FraudAlertDetailComponent } from './fraud-alert-detail.component';
import { LocalStorageService, STORAGE_KEYS } from '../../core/services/local-storage.service';
import { FraudAlert } from '../../shared/models/fraud.model';

const ALERT: FraudAlert = {
  id: 7, severity: 'CRITICAL', status: 'OPEN', reason: 'Decision BLOCK with score 80',
  createdAt: '2026-09-19T10:00:00Z', reviewedAt: null, reviewedBy: null,
  accountId: 3, accountNumber: '100000000001', transactionId: null, evaluationId: 11,
  riskScore: 80, decision: 'BLOCK', riskLevel: 'CRITICAL', amount: 60000, currency: 'INR',
  attemptedReference: 'TRF-1', transferReference: 'TRF-1', evaluationVersion: 'v1',
  factors: [
    { code: 'LARGE_TRANSACTION', description: 'Transaction exceeded configured threshold', score: 25 },
    { code: 'NEW_BENEFICIARY', description: 'Beneficiary was recently created', score: 15 },
    { code: 'RAPID_TRANSFERS', description: 'Multiple transfers occurred within the configured window', score: 20 },
  ],
};

/**
 * The alert screen used to be driven by `HttpTestingController`. It now reads
 * the same alert out of LocalStorage through `FraudService`, so these tests seed
 * the store instead of flushing a response. The local services resolve
 * synchronously, so no flushing is needed after `load()`.
 */
describe('FraudAlertDetailComponent', () => {
  let storage: LocalStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    storage = TestBed.inject(LocalStorageService);
    // jsdom keeps one `localStorage` per test file, so blank it to stay
    // independent of whatever the previous spec left behind.
    storage.clearAll();
    storage.write(STORAGE_KEYS.fraudAlerts, [ALERT]);
  });

  function create(): FraudAlertDetailComponent {
    return TestBed.runInInjectionContext(() => new FraudAlertDetailComponent());
  }

  it('loads the alert and exposes risk factors for rendering', () => {
    const component = create();
    component.load(7);

    expect(component.loading()).toBe(false);
    expect(component.alert()?.riskScore).toBe(80);
    expect(component.alert()?.factors.map((f) => f.code)).toEqual([
      'LARGE_TRANSACTION',
      'NEW_BENEFICIARY',
      'RAPID_TRANSFERS',
    ]);
    expect(component.factorScore(ALERT.factors[0])).toBe(25);
  });

  it('takes an alert under review', () => {
    const component = create();
    component.load(7);

    component.note = 'checking';
    component.takeReview();

    expect(component.alert()?.status).toBe('UNDER_REVIEW');
    expect(component.alert()?.reviewedBy).toBe('demo.analyst@nexabank.demo');
    expect(component.actionDone()).toContain('under review');
    expect(component.acting()).toBe(false);
  });

  it('resolves an alert under review', () => {
    const component = create();
    component.load(7);
    component.takeReview();

    component.resolve();
    expect(component.alert()?.status).toBe('RESOLVED');
    expect(component.actionDone()).toContain('resolved');
  });

  it('marks an alert as false positive', () => {
    const component = create();
    component.load(7);
    component.takeReview();

    component.markFalsePositive();
    expect(component.alert()?.status).toBe('FALSE_POSITIVE');
  });

  it('explains invalid transitions (400) without crashing', () => {
    const component = create();
    component.load(7);

    // OPEN cannot go straight to RESOLVED — the state machine rejects it.
    component.resolve();
    expect(component.actionError()).toContain('Invalid transition');
    expect(component.acting()).toBe(false);
    expect(component.alert()?.status).toBe('OPEN');
  });

  it('rejects an unusable alert id without calling the service', () => {
    const component = create();
    // ngOnInit reads the id from the route; with provideRouter([]) the param is
    // absent entirely, so the component must bail out cleanly rather than
    // treating the missing value as alert #0.
    component.ngOnInit();
    expect(component.error()).toBe('Invalid alert id.');
    expect(component.alert()).toBeNull();
  });

  it('handles load errors with loading state reset', () => {
    const component = create();

    component.load(999);
    expect(component.loading()).toBe(false);
    expect(component.error()).toContain('not found');
  });
});
