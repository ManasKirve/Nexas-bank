import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FraudService } from '../../core/services/fraud.service';
import { FraudAlert, FraudAlertStatus, FraudDecision, factorScore } from '../../shared/models/fraud.model';
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
      <header class="page-head">
        <div>
          <h2>Review queue</h2>
          <p class="muted">Explainable by design — every row shows score, decision and top factors.</p>
        </div>
      </header>
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
        <div class="card"><p class="muted">Loading alerts…</p></div>
      } @else if (error()) {
        <div class="card error-card" role="alert"><p>{{ error() }}</p></div>
      } @else if (alerts().length === 0) {
        <div class="card empty">
          <p>No alerts in this view.</p>
          <app-status-badge label="System nominal" tone="success" />
        </div>
      } @else {
        <div class="card">
          <div class="table-scroll">
            <table class="table stackable">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Severity</th>
                  <th class="num">Score</th>
                  <th>Decision</th>
                  <th class="num">Amount</th>
                  <th>Factors</th>
                  <th>Status</th>
                  <th class="right"></th>
                </tr>
              </thead>
              <tbody>
                @for (a of alerts(); track a.id) {
                  <tr>
                    <td data-label="ID"><span class="cell-ref">#{{ a.id }}</span></td>
                    <td data-label="Severity"><app-status-badge [label]="a.severity" [tone]="severityTone(a.severity)" /></td>
                    <td class="num" data-label="Score"><span class="score" [class.score-high]="a.riskLevel === 'HIGH' || a.riskLevel === 'CRITICAL'">{{ a.riskScore }}</span></td>
                    <td data-label="Decision"><app-status-badge [label]="a.decision" [tone]="decisionTone(a.decision)" /></td>
                    <td class="num cell-strong" data-label="Amount">{{ a.amount }} {{ a.currency }}</td>
                    <td class="muted factors-cell" data-label="Factors">{{ factorCodes(a) }}</td>
                    <td data-label="Status"><app-status-badge [label]="a.status" [tone]="statusTone(a.status)" /></td>
                    <td class="right" data-label="">
                      <a [routerLink]="['/fraud', a.id]" class="btn ghost small">Open</a>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <p class="table-foot">Showing {{ alerts().length }} of {{ totalElements() }} alerts.</p>
        </div>
      }
    </section>
  `,
  styles: [
    `
      .score {
        display: inline-block;
        min-width: 2.25rem;
        padding: 0.1rem 0.4rem;
        border-radius: var(--nx-r-sm);
        background: var(--nx-surface-3);
        color: var(--nx-ink-2);
        font-family: var(--nx-mono);
        font-size: 0.75rem;
        font-weight: 700;
        text-align: center;
      }
      .score-high {
        background: var(--nx-danger-soft);
        color: var(--nx-danger);
      }
      .factors-cell {
        max-width: 240px;
        font-size: 0.75rem;
        font-family: var(--nx-mono);
        overflow-wrap: anywhere;
      }
    `,
  ],
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

  decisionTone(decision: FraudDecision): 'danger' | 'warning' | 'success' {
    switch (decision) {
      case 'BLOCK':
        return 'danger';
      case 'REVIEW':
        return 'warning';
      default:
        return 'success';
    }
  }

  statusTone(status: FraudAlertStatus): 'success' | 'warning' | 'info' | 'neutral' {
    switch (status) {
      case 'OPEN':
        return 'warning';
      case 'UNDER_REVIEW':
        return 'info';
      case 'RESOLVED':
        return 'success';
      case 'FALSE_POSITIVE':
        return 'neutral';
    }
  }
}
