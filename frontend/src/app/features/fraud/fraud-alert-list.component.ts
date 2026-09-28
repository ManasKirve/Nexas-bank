import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FraudService } from '../../core/services/fraud.service';
import { FraudAlert, FraudAlertStatus, factorScore } from '../../shared/models/fraud.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

const STATUSES: Array<FraudAlertStatus | 'ALL'> = ['ALL', 'OPEN', 'UNDER_REVIEW', 'RESOLVED', 'FALSE_POSITIVE'];

/**
 * Analyst review queue: open alerts, high-risk filter, paged history.
 * Explainable by design — every row shows score, decision and top factors.
 */
@Component({
  selector: 'app-fraud-alert-list',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent],
  template: `
    <section class="page">
      <h2>Review queue</h2>
      <div class="toolbar" role="group" aria-label="Filter alerts by status">
        @for (s of statuses; track s) {
          <button
            type="button"
            class="btn ghost"
            [class.active]="filter() === s"
            (click)="selectFilter(s)"
          >
            {{ s }}
          </button>
        }
      </div>

      @if (loading()) {
        <div class="card"><p>Loading alerts…</p></div>
      } @else if (error()) {
        <div class="card error" role="alert"><p>{{ error() }}</p></div>
      } @else if (alerts().length === 0) {
        <div class="card"><p>No alerts. <app-status-badge label="System nominal" tone="success" /></p></div>
      } @else {
        <div class="card">
          <table class="table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Severity</th>
                <th>Score</th>
                <th>Decision</th>
                <th>Amount</th>
                <th>Factors</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (a of alerts(); track a.id) {
                <tr>
                  <td>#{{ a.id }}</td>
                  <td><app-status-badge [label]="a.severity" [tone]="severityTone(a.severity)" /></td>
                  <td>{{ a.riskScore }}</td>
                  <td>{{ a.decision }}</td>
                  <td>{{ a.amount }} {{ a.currency }}</td>
                  <td class="muted">{{ factorCodes(a) }}</td>
                  <td>{{ a.status }}</td>
                  <td><a [routerLink]="['/fraud', a.id]">Open</a></td>
                </tr>
              }
            </tbody>
          </table>
          <p class="muted">Showing {{ alerts().length }} of {{ totalElements() }} alerts.</p>
        </div>
      }
    </section>
  `,
})
export class FraudAlertListComponent implements OnInit {
  private readonly api = inject(FraudService);

  readonly statuses = STATUSES;
  readonly alerts = signal<FraudAlert[]>([]);
  readonly totalElements = signal(0);
  readonly filter = signal<FraudAlertStatus | 'ALL'>('OPEN');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  selectFilter(status: FraudAlertStatus | 'ALL'): void {
    this.filter.set(status);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const current = this.filter();
    const status: FraudAlertStatus | null = current === 'ALL' ? null : current;
    this.api.alerts(status, 0, 20).subscribe({
      next: (page) => {
        this.alerts.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 403 ? 'Access denied — analyst role required.' : 'Could not load alerts.');
        this.loading.set(false);
      },
    });
  }

  factorCodes(alert: FraudAlert): string {
    return alert.factors.map((f) => f.code).join(', ') || '—';
  }

  factorTotal(alert: FraudAlert): number {
    return alert.factors.reduce((sum, f) => sum + factorScore(f), 0);
  }

  severityTone(severity: string): 'danger' | 'warning' | 'info' | 'success' {
    switch (severity) {
      case 'CRITICAL':
      case 'HIGH':
        return 'danger';
      case 'MEDIUM':
        return 'warning';
      default:
        return 'info';
    }
  }
}
