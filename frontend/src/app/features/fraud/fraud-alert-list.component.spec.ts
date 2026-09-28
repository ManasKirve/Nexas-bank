import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { throwError } from 'rxjs';
import { FraudAlertListComponent } from './fraud-alert-list.component';
import { FraudService } from '../../core/services/fraud.service';
import { LocalStorageService, STORAGE_KEYS } from '../../core/services/local-storage.service';
import { FraudAlert } from '../../shared/models/fraud.model';

const ALERT: FraudAlert = {
  id: 7, severity: 'HIGH', status: 'OPEN', reason: 'Decision REVIEW with score 40',
  createdAt: '2026-09-19T10:00:00Z', reviewedAt: null, reviewedBy: null,
  accountId: 3, accountNumber: '100000000001', transactionId: null, evaluationId: 11,
  riskScore: 40, decision: 'REVIEW', riskLevel: 'MEDIUM', amount: 60000, currency: 'INR',
  attemptedReference: 'TRF-1', transferReference: 'TRF-1', evaluationVersion: 'v1',
  factors: [{ code: 'LARGE_TRANSACTION', description: 'big', score: 25 }],
};

/**
 * The queue used to be loaded with `HttpTestingController`. It now comes from
 * LocalStorage via `FraudService`, so the alerts are seeded into the store.
 * The two error-path tests keep using a spy, because there is no local code
 * path that produces a 403/500 — those statuses only ever came from the API.
 */
describe('FraudAlertListComponent', () => {
  let storage: LocalStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    storage = TestBed.inject(LocalStorageService);
    // jsdom keeps one `localStorage` per test file, so blank it to stay
    // independent of whatever the previous spec left behind.
    storage.clearAll();
    storage.write(STORAGE_KEYS.fraudAlerts, [ALERT]);
  });

  function create(): FraudAlertListComponent {
    return TestBed.runInInjectionContext(() => new FraudAlertListComponent());
  }

  it('renders risk factors for the loaded alerts', () => {
    const component = create();
    component.load();

    expect(component.loading()).toBe(false);
    expect(component.alerts()).toHaveLength(1);
    expect(component.factorCodes(component.alerts()[0])).toBe('LARGE_TRANSACTION');
    expect(component.factorTotal(component.alerts()[0])).toBe(25);
  });

  it('switching the status filter reloads from the store', () => {
    const component = create();
    component.load();

    component.selectFilter('UNDER_REVIEW');
    expect(component.alerts()).toHaveLength(0);

    component.selectFilter('OPEN');
    expect(component.alerts()).toHaveLength(1);
  });

  it('maps severity to badge tones', () => {
    const component = create();
    expect(component.severityTone('CRITICAL')).toBe('danger');
    expect(component.severityTone('HIGH')).toBe('danger');
    expect(component.severityTone('MEDIUM')).toBe('warning');
    expect(component.severityTone('LOW')).toBe('info');
  });

  it('reports the total element count', () => {
    const component = create();
    component.filter.set('ALL');
    component.load();
    expect(component.totalElements()).toBe(1);
  });

  it('shows an error message when loading fails', () => {
    const api = TestBed.inject(FraudService);
    vi.spyOn(api, 'alerts').mockReturnValue(throwError(() => ({ status: 500 }) as never));
    const component = create();
    component.load();
    expect(component.loading()).toBe(false);
    expect(component.error()).toContain('Could not load alerts');
  });

  it('explains 403 as analyst-role requirement', () => {
    const api = TestBed.inject(FraudService);
    vi.spyOn(api, 'alerts').mockReturnValue(throwError(() => ({ status: 403 }) as never));
    const component = create();
    component.load();
    expect(component.error()).toContain('analyst role');
  });
});
