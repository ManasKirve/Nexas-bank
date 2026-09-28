import { Component, input } from '@angular/core';
import { Transaction } from '../../shared/models/transaction.model';

/**
 * Presentational transaction-ledger table. Data and loading state flow in
 * via inputs; fetching and authorization stay in the container component.
 */
@Component({
  selector: 'app-transaction-history',
  standalone: true,
  template: `
    @if (loading()) {
      <p class="muted">Loading transactions…</p>
    } @else if (transactions().length === 0) {
      <p class="muted">No transactions yet.</p>
    } @else {
      <table class="ledger">
        <thead>
          <tr>
            <th>Reference</th>
            <th>Type</th>
            <th>Amount</th>
            <th>Status</th>
            <th>Balance before → after</th>
            <th>Transfer / counterparty</th>
            <th>Date</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          @for (txn of transactions(); track txn.transactionReference) {
            <tr>
              <td class="ref">{{ txn.transactionReference }}</td>
              <td>{{ txn.transactionType }}</td>
              <td class="num">{{ txn.amount }} {{ txn.currency }}</td>
              <td>{{ txn.status }}</td>
              <td class="num">{{ txn.balanceBefore }} → {{ txn.balanceAfter }}</td>
              <td class="ref">{{ transferContext(txn) }}</td>
              <td>{{ txn.createdAt }}</td>
              <td>{{ txn.description ?? '—' }}</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: [
    `
      .muted { color: #5f6368; }
      .ledger { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
      .ledger th, .ledger td { text-align: left; padding: 0.5rem 0.6rem; border-bottom: 1px solid #e1e3e6; }
      .ledger th { color: #5f6368; font-weight: 600; }
      .ref { font-family: monospace; font-size: 0.8rem; }
      .num { white-space: nowrap; }
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
}
