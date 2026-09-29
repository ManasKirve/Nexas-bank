import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { TransactionService } from '../../core/services/transaction.service';
import { Transaction, TransactionStatus } from '../../shared/models/transaction.model';

type TypeFilter = 'ALL' | 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';

/**
 * Transaction history for the acting demo customer, newest first.
 *
 * This replaces the two hard-coded `TXN-1001` / `TXN-1002` rows the Docker
 * build shipped as a Phase 1 placeholder. The ledger is the real one in
 * LocalStorage, so deposits, withdrawals and both transfer legs all appear
 * here after a refresh.
 */
@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [StatusBadgeComponent, DatePipe, DecimalPipe],
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <h1>Transactions</h1>
          <p class="muted">Immutable ledger for the demo customer selected in Admin.</p>
        </div>
        <span class="count-pill">{{ rows().length }} shown</span>
      </header>

      <div class="card filters">
        <div class="toolbar" role="group" aria-label="Filter transactions by type">
          @for (option of filterOptions; track option) {
            <button
              type="button"
              class="chip"
              [class.active]="filter() === option"
              (click)="filter.set(option)"
            >
              {{ option }}
            </button>
          }
        </div>
      </div>

      <div class="card">
        @if (loading()) {
          <p class="muted">Loading history…</p>
        } @else if (rows().length === 0) {
          <p class="muted">No transactions for this filter yet.</p>
        } @else {
          <div class="table-scroll">
            <table class="stackable">
              <thead>
                <tr>
                  <th>Reference</th>
                  <th>Date</th>
                  <th>Account</th>
                  <th>Type</th>
                  <th class="num">Amount</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                @for (t of rows(); track t.transactionReference) {
                  <tr>
                    <td data-label="Reference"><code>{{ t.transactionReference }}</code></td>
                    <td class="nowrap" data-label="Date">
                      <span class="cell-strong">{{ t.createdAt | date: 'dd MMM y' }}</span>
                      <span class="muted at-time">{{ t.createdAt | date: 'HH:mm' }}</span>
                    </td>
                    <td data-label="Account"><code>{{ t.accountNumber }}</code></td>
                    <td data-label="Type" class="cell-strong">{{ labelFor(t) }}</td>
                    <td class="num" [class.credit]="isCredit(t)" [class.debit]="!isCredit(t)" data-label="Amount">
                      {{ isCredit(t) ? '+' : '−' }}{{ t.amount | number: '1.2-2' }} {{ t.currency }}
                    </td>
                    <td data-label="Status">
                      <app-status-badge [label]="t.status" [tone]="statusTone(t.status)" />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </section>
  `,
  styles: [
    `
      .count-pill {
        display: inline-flex;
        align-items: center;
        padding: 0.25rem 0.7rem;
        border: 1px solid var(--nx-border);
        border-radius: var(--nx-r-pill);
        background: var(--nx-surface);
        color: var(--nx-muted);
        font-size: 0.75rem;
        font-weight: 600;
        white-space: nowrap;
      }
      .filters {
        padding: 0.7rem 0.9rem;
      }
      .at-time {
        display: block;
        font-size: 0.6875rem;
        color: var(--nx-faint);
      }
    `,
  ],
})
export class TransactionsComponent implements OnInit {
  private readonly transactionsApi = inject(TransactionService);

  readonly filter = signal<TypeFilter>('ALL');
  readonly all = signal<Transaction[]>([]);
  readonly loading = signal(true);

  readonly filterOptions: TypeFilter[] = ['ALL', 'DEPOSIT', 'WITHDRAWAL', 'TRANSFER'];

  /** The active chip narrows the table without another query. */
  readonly rows = computed<Transaction[]>(() => {
    const filter = this.filter();
    const all = this.all();
    if (filter === 'ALL') {
      return all;
    }
    if (filter === 'TRANSFER') {
      return all.filter((t) => t.transactionType.startsWith('TRANSFER'));
    }
    return all.filter((t) => t.transactionType === filter);
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.transactionsApi.ownHistory(0, 200).subscribe((page) => {
      this.all.set(page.content);
      this.loading.set(false);
    });
  }

  isCredit(t: Transaction): boolean {
    return t.transactionType === 'DEPOSIT' || t.transactionType === 'TRANSFER_CREDIT';
  }

  labelFor(t: Transaction): string {
    switch (t.transactionType) {
      case 'DEPOSIT':
        return 'Deposit';
      case 'WITHDRAWAL':
        return 'Withdrawal';
      case 'TRANSFER_DEBIT':
        return `Transfer out → ${t.counterpartyAccountNumber ?? '—'}`;
      case 'TRANSFER_CREDIT':
        return `Transfer in ← ${t.counterpartyAccountNumber ?? '—'}`;
    }
  }

  statusTone(status: TransactionStatus): 'success' | 'warning' | 'danger' | 'neutral' {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'PENDING':
        return 'warning';
      case 'FAILED':
      case 'REVERSED':
        return 'danger';
      default:
        return 'neutral';
    }
  }
}
