import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AccountService } from '../../core/services/account.service';
import { Account } from '../../shared/models/account.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'app-account-list',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent],
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <h1>Accounts</h1>
          <p class="muted">Bank accounts backed by the NexaBank API.</p>
        </div>
        <a routerLink="/accounts/new" class="btn primary">Open account</a>
      </header>

      @if (loading()) {
        <div class="card"><p>Loading accounts…</p></div>
      } @else if (error()) {
        <div class="card error">
          <p>{{ error() }}</p>
          <button type="button" class="btn ghost" (click)="reload()">Retry</button>
        </div>
      } @else if (accounts().length === 0) {
        <div class="card empty">
          <p>No accounts yet. Open the first account to get started.</p>
          <a routerLink="/accounts/new" class="btn primary">Open account</a>
        </div>
      } @else {
        <div class="card table-card">
          <table>
            <thead>
              <tr><th>Number</th><th>Customer</th><th>Type</th><th>Balance</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              @for (a of accounts(); track a.id) {
                <tr>
                  <td>{{ a.accountNumber }}</td>
                  <td>{{ a.customerNumber }}</td>
                  <td>{{ a.accountType }}</td>
                  <td>{{ a.balance }} {{ a.currency }}</td>
                  <td><app-status-badge [label]="a.status" [tone]="tone(a.status)" /></td>
                  <td class="actions"><a [routerLink]="['/accounts', a.id]">View</a></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: [
    `
      .page-head { display: flex; justify-content: space-between; align-items: center; gap: 1rem; margin-bottom: 1.25rem; flex-wrap: wrap; }
      h1 { margin: 0; }
      .muted { color: #5f6368; margin: 0.25rem 0 0; }
      .table-card { overflow-x: auto; }
      table { width: 100%; border-collapse: collapse; }
      th, td { text-align: left; padding: 0.6rem; border-bottom: 1px solid #e8eaed; font-size: 0.9rem; }
      th { color: #5f6368; }
      .actions { text-align: right; }
      .empty, .error { display: grid; gap: 0.75rem; justify-items: start; }
      .btn { text-decoration: none; padding: 0.55rem 1rem; border-radius: 8px; font-weight: 600; font-size: 0.9rem; border: 0; cursor: pointer; }
      .btn.primary { background: #174ea6; color: #fff; }
      .btn.ghost { border: 1px solid #2f6fed; color: #2f6fed; background: #fff; }
    `,
  ],
})
export class AccountListComponent implements OnInit {
  private readonly accountsApi = inject(AccountService);
  readonly accounts = signal<Account[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.accountsApi.getAll().subscribe({
      next: (rows) => {
        this.accounts.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load accounts. Is the backend running?');
        this.loading.set(false);
      },
    });
  }

  tone(status: Account['status']): 'success' | 'warning' | 'danger' | 'neutral' {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'FROZEN':
        return 'warning';
      default:
        return 'neutral';
    }
  }
}
