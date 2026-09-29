import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { Transaction, TransactionStatus } from '../../shared/models/transaction.model';

const TYPE_ICONS: Record<Transaction['transactionType'], string> = {
  DEPOSIT: 'M12 4.5v11M7.5 11 12 15.5 16.5 11M4.5 19.5h15',
  WITHDRAWAL: 'M12 19.5v-11M7.5 13 12 8.5 16.5 13M4.5 4.5h15',
  TRANSFER_DEBIT: 'M4 12h15M13.5 6.5 19 12l-5.5 5.5',
  TRANSFER_CREDIT: 'M20 12H5M10.5 6.5 5 12l5.5 5.5',
};

/**
 * Presentational transaction-ledger table. Data and loading state flow in
 * via inputs; fetching and authorization stay in the container component.
 */
@Component({
  selector: 'app-transaction-history',
  standalone: true,
  imports: [DatePipe, StatusBadgeComponent],
  template: `
    @if (loading()) {
      <p class="muted">Loading transactions…</p>
    } @else if (transactions().length === 0) {
      <p class="muted">No transactions yet.</p>
    } @else {
      <div class="table-scroll">
        <table class="ledger stackable">
          <thead>
            <tr>
              <th>Reference</th>
              <th>Type</th>
              <th>Date</th>
              <th class="num">Amount</th>
              <th>Status</th>
              <th class="num">Balance before → after</th>
              <th>Transfer / counterparty</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            @for (txn of transactions(); track txn.transactionReference) {
              <tr>
                <td class="ref" data-label="Reference">
                  <span class="ref-icon" [class.credit-icon]="isCredit(txn)" [class.debit-icon]="!isCredit(txn)" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
                      <path [attr.d]="typeIcon(txn)" />
                    </svg>
                  </span>
                  <span class="cell-ref">{{ txn.transactionReference }}</span>
                </td>
                <td data-label="Type"><span class="type-chip">{{ txn.transactionType }}</span></td>
                <td class="nowrap" data-label="Date">
                  <span class="muted">{{ txn.createdAt | date: 'dd MMM y' }}</span>
                  <span class="muted at-time">{{ txn.createdAt | date: 'HH:mm' }}</span>
                </td>
                <td class="num" [class.credit]="isCredit(txn)" [class.debit]="!isCredit(txn)" data-label="Amount">
                  {{ isCredit(txn) ? '+' : '−' }}{{ txn.amount }} {{ txn.currency }}
                </td>
                <td data-label="Status">
                  <app-status-badge [label]="txn.status" [tone]="statusTone(txn.status)" />
                </td>
                <td class="num muted" data-label="Balance">{{ txn.balanceBefore }} → {{ txn.balanceAfter }}</td>
                <td class="counterparty" data-label="Counterparty">{{ transferContext(txn) }}</td>
                <td class="desc" data-label="Description">{{ txn.description ?? '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [
    `
      .ref {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        white-space: nowrap;
      }
      .ref-icon {
        display: grid;
        place-items: center;
        width: 28px;
        height: 28px;
        flex: 0 0 28px;
        border-radius: var(--nx-r-sm);
        background: var(--nx-surface-3);
        color: var(--nx-muted);
      }
      .ref-icon svg {
        width: 15px;
        height: 15px;
      }
      .ref-icon.credit-icon {
        background: var(--nx-success-soft);
        color: var(--nx-success);
      }
      .ref-icon.debit-icon {
        background: var(--nx-surface-3);
        color: var(--nx-ink-2);
      }
      .at-time {
        display: block;
        font-size: 0.6875rem;
        color: var(--nx-faint);
      }
      .type-chip {
        display: inline-block;
        padding: 0.1rem 0.45rem;
        border: 1px solid var(--nx-border);
        border-radius: var(--nx-r-sm);
        background: var(--nx-surface-2);
        color: var(--nx-ink-2);
        font-family: var(--nx-mono);
        font-size: 0.6875rem;
        font-weight: 600;
        white-space: nowrap;
      }
      .desc {
        color: var(--nx-muted);
        max-width: 200px;
        overflow-wrap: anywhere;
      }
      /* Unlike the TRF- reference, the counterparty can be a long free-text
         value, so it is allowed to wrap instead of forcing the ledger wider. */
      .counterparty {
        color: var(--nx-ink-2);
        max-width: 190px;
        overflow-wrap: anywhere;
      }
      .ledger td.num {
        font-size: 0.8125rem;
      }
    `,
  ],
})
export class TransactionHistoryComponent {
  readonly transactions = input<Transaction[]>([]);
  readonly loading = input<boolean>(false);

  /** Transfer legs show their shared TRF-… reference plus direction; plain legs show '—'. */
  transferContext(txn: Transaction): string {
    if (txn.transactionType === 'TRANSFER_DEBIT' || txn.transactionType === 'TRANSFER_CREDIT') {
      const direction = txn.transactionType === 'TRANSFER_DEBIT' ? 'to' : 'from';
      return `${txn.transferReference ?? txn.transactionReference} ${direction} ${txn.counterpartyAccountNumber ?? '?'}`;
    }
    return '—';
  }

  isCredit(txn: Transaction): boolean {
    return txn.transactionType === 'DEPOSIT' || txn.transactionType === 'TRANSFER_CREDIT';
  }

  typeIcon(txn: Transaction): string {
    return TYPE_ICONS[txn.transactionType];
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
