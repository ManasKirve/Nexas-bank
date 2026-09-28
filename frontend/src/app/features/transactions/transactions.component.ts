import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { TransactionService } from '../../core/services/transaction.service';
import { Transaction } from '../../shared/models/transaction.model';

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
      <h1>Transactions</h1>
      <p class="muted">Immutable ledger for the demo customer selected in Admin.</p>

      <div class="card filters">
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

      <div class="card table-card">
        @if (loading()) {
          <p>Loading history…</p>
        } @else if (rows().length === 0) {
          <p class="muted">No transactions for this filter yet.</p>
        } @else {
          <table>
            <thead>
              <tr>
                <th>Reference</th>
                <th>Date</th>
                <th>Account</th>
                <th>Type</th>
                <th>Amount</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              @for (t of rows(); track t.transactionReference) {
                <tr>
                  <td><code>{{ t.transactionReference }}</code></td>
                  <td>{{ t.createdAt | date: 'medium' }}</td>
                  <td>{{ t.accountNumber }}</td>
                  <td>{{ labelFor(t) }}</td>
                  <td [class.credit]="isCredit(t)" [class.debit]="!isCredit(t)">
                    {{ isCredit(t) ? '+' : '−' }}{{ t.amount | number: '1.2-2' }} {{ t.currency }}
                  </td>
                  <td>
                    <app-status-badge
                      [label]="t.status"
                      [tone]="t.status === 'COMPLETED' ? 'success' : 'warning'"
                    />
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </section>
  `,
  styles: [
    `
      h1 { margin: 0 0 0.25rem; }
      .muted { color: #5f6368; }
      .card { margin-top: 1rem; padding: 1rem 1.25rem; border: 1px solid #e8eaed; border-radius: 12px; }
      .filters { display: flex; gap: 0.5rem; flex-wrap: wrap; padding: 0.75rem 1.25rem; }
      .chip { border: 1px solid #dadce0; background: #fff; border-radius: 999px; padding: 0.3rem 0.9rem; font-size: 0.8rem; font-weight: 600; cursor: pointer; }
      .chip.active { background: #174ea6; border-color: #174ea6; color: #fff; }
      table { width: 100%; border-collapse: collapse; }
      th, td { text-align: left; padding: 0.6rem; border-bottom: 1px solid #e8eaed; }
      th { color: #5f6368; }
      code { background: #f1f3f4; padding: 0.05rem 0.3rem; border-radius: 4px; }
      .credit { color: #137333; }
      .debit { color: #a50e0e; }
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
}
