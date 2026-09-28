import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { FraudService } from '../../core/services/fraud.service';
import { FraudAlert, factorScore } from '../../shared/models/fraud.model';
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
      <p><a routerLink="/fraud">← Back to fraud monitoring</a></p>

      @if (loading()) {
        <div class="card"><p>Loading alert…</p></div>
      } @else if (error()) {
        <div class="card error" role="alert"><p>{{ error() }}</p></div>
      } @else if (alert()) {
        <h1>Alert #{{ alert()!.id }}</h1>
        <p class="muted">
          {{ alert()!.attemptedReference ?? alert()!.transferReference ?? 'held attempt' }} ·
          account {{ alert()!.accountNumber }} · evaluation {{ alert()!.evaluationVersion }}
        </p>

        <div class="grid">
          <div class="card">
            <h3>Evaluation</h3>
            <p>Risk score: <strong>{{ alert()!.riskScore }}</strong> ({{ alert()!.riskLevel }})</p>
            <p>Decision: <app-status-badge [label]="alert()!.decision" tone="warning" /></p>
            <p>Severity: {{ alert()!.severity }} · Status: {{ alert()!.status }}</p>
            <p>Amount: {{ alert()!.amount }} {{ alert()!.currency }}</p>
            <p class="muted">Created {{ alert()!.createdAt }}</p>
            @if (alert()!.reviewedBy) {
              <p class="muted">Last action by {{ alert()!.reviewedBy }} at {{ alert()!.reviewedAt }}</p>
            }
          </div>

          <div class="card">
            <h3>Triggered risk factors</h3>
            @if (alert()!.factors.length === 0) {
              <p class="muted">No factors recorded.</p>
            } @else {
              <ul>
                @for (f of alert()!.factors; track f.code) {
                  <li><strong>{{ f.code }}</strong> (+{{ factorScore(f) }}) — {{ f.description }}</li>
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
            <button type="button" class="btn" (click)="takeReview()" [disabled]="acting() || alert()!.status !== 'OPEN'">
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
            <p class="error" role="alert">{{ actionError() }}</p>
          }
          @if (actionDone()) {
            <p class="muted" role="status">{{ actionDone() }}</p>
          }
        </div>
      }
    </section>
  `,
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
