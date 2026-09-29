import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FraudService } from '../../core/services/fraud.service';
import { FraudDashboard, FraudAlertStatus } from '../../shared/models/fraud.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { FraudAlertListComponent } from './fraud-alert-list.component';

/**
 * Phase 6 fraud analyst dashboard: queue counts, high-risk load, blocked vs
 * review volumes and the live review queue. Analyst/Admin only (route +
 * backend guarded).
 */
@Component({
  selector: 'app-fraud',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent, FraudAlertListComponent],
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <h1>Fraud Monitoring</h1>
          <p class="muted">Deterministic risk pipeline: Transaction → Fraud Evaluation → Risk Factors → Decision → Alert → Analyst Review.</p>
        </div>
      </header>

      @if (loading()) {
        <div class="card"><p class="muted">Loading fraud overview…</p></div>
      } @else if (error()) {
        <div class="card error-card" role="alert"><p>{{ error() }}</p></div>
      } @else if (summary()) {
        <div class="grid cards">
          <div class="card kpi">
            <span class="overline">Open alerts</span>
            <p class="stat">{{ summary()!.openAlerts }}</p>
            <span class="kpi-meta">Awaiting analyst review</span>
          </div>
          <div class="card kpi">
            <span class="overline">Under review</span>
            <p class="stat">{{ summary()!.underReviewAlerts }}</p>
            <span class="kpi-meta">Claimed by an analyst</span>
          </div>
          <div class="card kpi kpi-danger">
            <span class="overline">High-risk open</span>
            <p class="stat">{{ summary()!.highRiskAlerts }}</p>
            <span class="kpi-meta">Critical or high risk level</span>
          </div>
          <div class="card kpi kpi-danger">
            <span class="overline">Blocked evaluations</span>
            <p class="stat">{{ summary()!.blockedEvaluations }}</p>
            <span class="kpi-meta">Money never moved</span>
          </div>
          <div class="card kpi kpi-warning">
            <span class="overline">Review evaluations</span>
            <p class="stat">{{ summary()!.reviewEvaluations }}</p>
            <span class="kpi-meta">Held for a decision</span>
          </div>
          <div class="card kpi kpi-success">
            <span class="overline">Resolved / false positive</span>
            <p class="stat">{{ summary()!.resolvedAlerts }} / {{ summary()!.falsePositiveAlerts }}</p>
            <span class="kpi-meta">Closed out of the queue</span>
          </div>
        </div>

        @if (summary()!.recentAlerts.length > 0) {
          <div class="card">
            <h3>Recent high-priority work</h3>
            <ul class="ruled">
              @for (a of summary()!.recentAlerts.slice(0, 5); track a.id) {
                <li>
                  <a [routerLink]="['/fraud', a.id]" class="alert-link">Alert #{{ a.id }}</a>
                  <span class="alert-meta">score {{ a.riskScore }} · {{ a.riskLevel }} · {{ a.decision }}</span>
                  <app-status-badge [label]="a.status" [tone]="statusTone(a.status)" />
                </li>
              }
            </ul>
          </div>
        }
      }

      <app-fraud-alert-list />
    </section>
  `,
  styles: [
    `
      .kpi {
        display: grid;
        gap: 0.2rem;
        align-content: start;
        border-top: 2px solid var(--nx-border-2);
        transition: box-shadow var(--nx-ease), transform var(--nx-ease);
      }
      .kpi:hover {
        box-shadow: var(--nx-shadow-md);
        transform: translateY(-1px);
      }
      .kpi .stat {
        margin: 0.15rem 0 0.1rem;
      }
      .kpi-meta {
        font-size: 0.6875rem;
        color: var(--nx-faint);
      }
      .kpi-danger {
        border-top-color: var(--nx-danger-line);
      }
      .kpi-warning {
        border-top-color: var(--nx-warning-line);
      }
      .kpi-success {
        border-top-color: var(--nx-success-line);
      }
      .alert-link {
        font-weight: 650;
        white-space: nowrap;
      }
      .alert-meta {
        flex: 1 1 auto;
        font-size: 0.75rem;
        color: var(--nx-muted);
      }
    `,
  ],
})
export class FraudComponent implements OnInit {
  private readonly api = inject(FraudService);

  readonly summary = signal<FraudDashboard | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  statusTone(status: FraudAlertStatus): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
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

  ngOnInit(): void {
    this.loading.set(true);
    this.api.dashboard().subscribe({
      next: (dashboard) => {
        this.summary.set(dashboard);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 403 ? 'Access denied — analyst role required.' : 'Could not load fraud overview.');
        this.loading.set(false);
      },
    });
  }
}
