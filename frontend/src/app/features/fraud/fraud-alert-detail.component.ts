import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { FraudService } from '../../core/services/fraud.service';
import { FraudAlert, FraudAlertStatus, FraudDecision, factorScore } from '../../shared/models/fraud.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

/**
 * Analyst alert detail: transaction context, risk score, decision, triggered
 * risk factors, evaluation version and the review workflow actions
 * (review → resolve / false-positive).
 */
@Component({
  selector: 'app-fraud-alert-detail',
  standalone: true,
  imports: [RouterLink, FormsModule, StatusBadgeComponent],
  template: `
    <section class="page">
      <a routerLink="/fraud" class="back">← Back to fraud monitoring</a>

      @if (loading()) {
        <div class="card"><p class="muted">Loading alert…</p></div>
      } @else if (error()) {
        <div class="card error-card" role="alert"><p>{{ error() }}</p></div>
      } @else if (alert()) {
        <div class="risk-head">
          <div class="risk-score" [class.risk-critical]="alert()!.riskLevel === 'CRITICAL'" [class.risk-high]="alert()!.riskLevel === 'HIGH'">
            <span class="overline">Risk score</span>
            <p class="risk-value">{{ alert()!.riskScore }}</p>
            <span class="risk-level">{{ alert()!.riskLevel }}</span>
          </div>
          <div class="risk-meta">
            <h1>Alert #{{ alert()!.id }}</h1>
            <p class="muted">
              {{ alert()!.attemptedReference ?? alert()!.transferReference ?? 'held attempt' }} ·
              account {{ alert()!.accountNumber }} · evaluation {{ alert()!.evaluationVersion }}
            </p>
            <div class="badge-row">
              <app-status-badge [label]="alert()!.severity" tone="danger" />
              <app-status-badge [label]="alert()!.decision" [tone]="decisionTone(alert()!.decision)" />
              <app-status-badge [label]="alert()!.status" [tone]="statusTone(alert()!.status)" />
            </div>
          </div>
        </div>

        <div class="grid">
          <div class="card">
            <h3>Evaluation</h3>
            <dl>
              <div><dt>Risk score</dt><dd>{{ alert()!.riskScore }} ({{ alert()!.riskLevel }})</dd></div>
              <div><dt>Decision</dt><dd>{{ alert()!.decision }}</dd></div>
              <div><dt>Status</dt><dd>{{ alert()!.status }}</dd></div>
              <div><dt>Amount</dt><dd>{{ alert()!.amount }} {{ alert()!.currency }}</dd></div>
              <div><dt>Account</dt><dd>{{ alert()!.accountNumber }}</dd></div>
              <div><dt>Created</dt><dd>{{ alert()!.createdAt }}</dd></div>
            </dl>
            @if (alert()!.reviewedBy) {
              <p class="note">Last action by {{ alert()!.reviewedBy }} at {{ alert()!.reviewedAt }}</p>
            }
          </div>

          <div class="card">
            <h3>Triggered risk factors</h3>
            @if (alert()!.factors.length === 0) {
              <p class="muted">No factors recorded.</p>
            } @else {
              <ul class="factors">
                @for (f of alert()!.factors; track f.code) {
                  <li>
                    <span class="factor-head">
                      <code>{{ f.code }}</code>
                      <span class="factor-score">+{{ factorScore(f) }}</span>
                    </span>
                    <span class="muted">{{ f.description }}</span>
                  </li>
                }
              </ul>
            }
          </div>
        </div>

        <div class="card">
          <h3>Analyst actions</h3>
          <label>Note (optional)
            <input type="text" [(ngModel)]="note" maxlength="500" placeholder="Reason for this action" />
          </label>
          <div class="toolbar">
            <button type="button" class="btn soft" (click)="takeReview()" [disabled]="acting() || alert()!.status !== 'OPEN'">
              {{ acting() ? 'Working…' : 'Take under review' }}
            </button>
            <button type="button" class="btn primary" (click)="resolve()" [disabled]="acting() || alert()!.status !== 'UNDER_REVIEW'">
              Resolve
            </button>
            <button type="button" class="btn ghost" (click)="markFalsePositive()" [disabled]="acting() || alert()!.status !== 'UNDER_REVIEW'">
              Mark false positive
            </button>
          </div>
          @if (actionError()) {
            <p class="form-error" role="alert">{{ actionError() }}</p>
          }
          @if (actionDone()) {
            <p class="action" role="status">{{ actionDone() }}</p>
          }
        </div>
      }
    </section>
  `,
  styles: [
    `
      .risk-head {
        display: flex;
        align-items: center;
        gap: 1.25rem;
        flex-wrap: wrap;
        padding: 1.25rem 1.5rem;
        border: 1px solid var(--nx-border);
        border-radius: var(--nx-r-lg);
        background: var(--nx-surface);
        box-shadow: var(--nx-shadow-sm);
      }
      .risk-score {
        display: grid;
        justify-items: center;
        min-width: 108px;
        padding: 0.85rem 1rem;
        border: 1px solid var(--nx-border);
        border-radius: var(--nx-r-md);
        background: var(--nx-surface-2);
      }
      .risk-score.risk-high {
        border-color: var(--nx-warning-line);
        background: var(--nx-warning-soft);
      }
      .risk-score.risk-critical {
        border-color: var(--nx-danger-line);
        background: var(--nx-danger-soft);
      }
      .risk-value {
        font-size: 1.875rem;
        font-weight: 700;
        letter-spacing: -0.028em;
        line-height: 1.1;
        font-variant-numeric: tabular-nums;
      }
      .risk-critical .risk-value {
        color: var(--nx-danger);
      }
      .risk-high .risk-value {
        color: var(--nx-warning);
      }
      .risk-level {
        font-size: 0.6875rem;
        font-weight: 700;
        letter-spacing: 0.07em;
        color: var(--nx-muted);
      }
      .risk-meta {
        min-width: 0;
      }
      .risk-meta h1 {
        margin: 0;
      }
      .badge-row {
        display: flex;
        flex-wrap: wrap;
        gap: 0.4rem;
        margin-top: 0.6rem;
      }
      .action {
        margin-top: 0.75rem;
        color: var(--nx-success);
        font-size: 0.8125rem;
        font-weight: 500;
      }
      .toolbar {
        margin-top: 0.85rem;
      }
      .factors .muted {
        font-size: 0.75rem;
      }
    `,
  ],
})
export class FraudAlertDetailComponent implements OnInit {
  private readonly api = inject(FraudService);
  private readonly route = inject(ActivatedRoute);

  readonly alert = signal<FraudAlert | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly acting = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly actionDone = signal<string | null>(null);
  note = '';

  ngOnInit(): void {
    // `Number(null)` is 0, so a missing or blank param has to be rejected
    // explicitly — otherwise the screen would quietly open alert #0.
    const raw = this.route.snapshot.paramMap.get('id');
    const id = raw === null || raw.trim() === '' ? Number.NaN : Number(raw);
    if (!Number.isFinite(id)) {
      this.error.set('Invalid alert id.');
      return;
    }
    this.load(id);
  }

  load(id: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.alert(id).subscribe({
      next: (alert) => {
        this.alert.set(alert);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(
          err.status === 404 ? 'Alert not found.' : err.status === 403 ? 'Access denied.' : 'Could not load alert.',
        );
        this.loading.set(false);
      },
    });
  }

  factorScore = factorScore;

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

  takeReview(): void {
    this.runAction((id, note) => this.api.review(id, note), 'Alert taken under review.');
  }

  resolve(): void {
    this.runAction((id, note) => this.api.resolve(id, note), 'Alert resolved.');
  }

  markFalsePositive(): void {
    this.runAction((id, note) => this.api.falsePositive(id, note), 'Alert marked as false positive.');
  }

  private runAction(
    call: (id: number, note: string | null) => Observable<FraudAlert>,
    doneMessage: string,
  ): void {
    const current = this.alert();
    if (!current) {
      return;
    }
    this.acting.set(true);
    this.actionError.set(null);
    this.actionDone.set(null);
    call(current.id, this.note.trim() ? this.note.trim() : null).subscribe({
      next: (updated) => {
        this.alert.set(updated);
        this.actionDone.set(doneMessage);
        this.acting.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.actionError.set(
          err.status === 400
            ? 'Invalid transition for the current alert status.'
            : err.status === 403
              ? 'Access denied.'
              : 'Action failed. Please retry.',
        );
        this.acting.set(false);
      },
    });
  }
}
