import { Component, OnInit, inject, signal } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { HealthService, BackendConnectionState } from '../../core/services/health.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { DashboardSummary } from '../../shared/models/dashboard.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

/**
 * Admin console (ADMIN role only).
 *
 * Shows the authenticated session and live platform metrics read from the
 * backend. User and role administration is owned by the server (the dev seeder
 * and the users table), so this screen is read-only and never touches
 * client storage.
 */
@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [StatusBadgeComponent],
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <h1>Admin</h1>
          <p class="muted">Platform overview and session details from the NexaBank API.</p>
        </div>
      </header>

      <div class="card">
        <h3>Session</h3>
        @if (user(); as u) {
          <dl class="counts">
            <div><dt>Username</dt><dd>{{ u.username }}</dd></div>
            <div><dt>Role</dt><dd>{{ u.role }}</dd></div>
            <div><dt>Email</dt><dd>{{ u.email }}</dd></div>
            <div><dt>Status</dt><dd>{{ u.status }}</dd></div>
          </dl>
        } @else {
          <p class="muted">No active session.</p>
        }
      </div>

      <div class="card">
        <h3>Backend</h3>
        <p class="muted">
          Data source:
          @switch (connection()) {
            @case ('checking') { <app-status-badge label="Checking…" tone="info" /> }
            @case ('connected') { <app-status-badge label="API online" tone="success" /> }
            @default { <app-status-badge label="Unavailable" tone="danger" /> }
          }
        </p>
        <button type="button" class="btn ghost" (click)="refresh()">Re-check</button>
      </div>

      <div class="card">
        <h3>Platform metrics</h3>
        @if (loading()) {
          <p class="muted">Loading metrics…</p>
        } @else if (summary(); as s) {
          <dl class="counts">
            <div><dt>Customers</dt><dd>{{ s.totalCustomers }}</dd></div>
            <div><dt>Accounts</dt><dd>{{ s.totalAccounts }}</dd></div>
            <div><dt>Active accounts</dt><dd>{{ s.activeAccounts }}</dd></div>
            <div><dt>Frozen accounts</dt><dd>{{ s.frozenAccounts }}</dd></div>
          </dl>
        } @else {
          <p class="muted">Metrics are unavailable right now.</p>
        }
      </div>
    </section>
  `,
  styles: [
    `
      .counts {
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 0.5rem;
        margin-top: 0.85rem;
      }
      .counts > div {
        flex-direction: column;
        align-items: flex-start;
        gap: 0.15rem;
        padding: 0.6rem 0.75rem;
        border: 1px solid var(--nx-border);
        border-radius: var(--nx-r-md);
        background: var(--nx-surface-2);
        border-bottom: 1px solid var(--nx-border);
      }
      .counts dt {
        min-width: 0;
        font-size: 0.6875rem;
        font-weight: 650;
        letter-spacing: 0.06em;
        text-transform: uppercase;
      }
      .counts dd {
        font-size: 1.125rem;
        font-variant-numeric: tabular-nums;
        overflow-wrap: anywhere;
      }
    `,
  ],
})
export class AdminComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly health = inject(HealthService);
  private readonly dashboard = inject(DashboardService);

  readonly user = this.auth.user;
  readonly connection = signal<BackendConnectionState>('checking');
  readonly summary = signal<DashboardSummary | null>(null);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.connection.set('checking');
    this.health.connectionState().subscribe((state) => this.connection.set(state));

    this.loading.set(true);
    this.dashboard.getSummary().subscribe({
      next: (s) => {
        this.summary.set(s);
        this.loading.set(false);
      },
      error: () => {
        this.summary.set(null);
        this.loading.set(false);
      },
    });
  }
}
