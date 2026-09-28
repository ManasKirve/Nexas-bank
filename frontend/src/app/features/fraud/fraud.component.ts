import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FraudService } from '../../core/services/fraud.service';
import { FraudDashboard } from '../../shared/models/fraud.model';
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
      <h1>Fraud Monitoring</h1>
      <p class="muted">Deterministic risk pipeline: Transaction → Fraud Evaluation → Risk Factors → Decision → Alert → Analyst Review.</p>

      @if (loading()) {
        <div class="card"><p>Loading fraud overview…</p></div>
      } @else if (error()) {
        <div class="card error" role="alert"><p>{{ error() }}</p></div>
      } @else if (summary()) {
        <div class="grid">
          <div class="card"><h3>Open alerts</h3><p class="stat">{{ summary()!.openAlerts }}</p></div>
          <div class="card"><h3>Under review</h3><p class="stat">{{ summary()!.underReviewAlerts }}</p></div>
          <div class="card"><h3>High-risk open</h3><p class="stat">{{ summary()!.highRiskAlerts }}</p></div>
          <div class="card"><h3>Blocked evaluations</h3><p class="stat">{{ summary()!.blockedEvaluations }}</p></div>
          <div class="card"><h3>Review evaluations</h3><p class="stat">{{ summary()!.reviewEvaluations }}</p></div>
          <div class="card"><h3>Resolved / false positive</h3><p class="stat">{{ summary()!.resolvedAlerts }} / {{ summary()!.falsePositiveAlerts }}</p></div>
        </div>

        @if (summary()!.recentAlerts.length > 0) {
          <div class="card">
            <h3>Recent high-priority work</h3>
            <ul>
              @for (a of summary()!.recentAlerts.slice(0, 5); track a.id) {
                <li>
                  <a [routerLink]="['/fraud', a.id]">Alert #{{ a.id }}</a>
                  — score {{ a.riskScore }} ({{ a.decision }})
                  <app-status-badge [label]="a.status" tone="info" />
                </li>
              }
            </ul>
          </div>
        }
      }

      <app-fraud-alert-list />
    </section>
  `,
})
export class FraudComponent implements OnInit {
  private readonly api = inject(FraudService);

  readonly summary = signal<FraudDashboard | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

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
