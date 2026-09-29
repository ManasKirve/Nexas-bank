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
        <div class="card"><p class="muted">Loading accounts…</p></div>
      } @else if (error()) {
        <div class="card error-card">
          <p>{{ error() }}</p>
          <button type="button" class="btn ghost" (click)="reload()">Retry</button>
        </div>
      } @else if (accounts().length === 0) {
        <div class="card empty">
          <p>No accounts yet. Open the first account to get started.</p>
          <a routerLink="/accounts/new" class="btn primary">Open account</a>
        </div>
      } @else {
        <div class="card">
          <div class="table-scroll">
            <table class="stackable">
              <thead>
                <tr>
                  <th>Number</th>
                  <th>Customer</th>
                  <th>Type</th>
                  <th class="num">Balance</th>
                  <th>Status</th>
                  <th class="right"></th>
                </tr>
              </thead>
              <tbody>
                @for (a of accounts(); track a.id) {
                  <tr>
                    <td data-label="Number"><code>{{ a.accountNumber }}</code></td>
                    <td data-label="Customer"><code>{{ a.customerNumber }}</code></td>
                    <td data-label="Type" class="cell-strong">{{ a.accountType }}</td>
                    <td class="num cell-strong" data-label="Balance">{{ a.balance }} {{ a.currency }}</td>
                    <td data-label="Status"><app-status-badge [label]="a.status" [tone]="tone(a.status)" /></td>
                    <td class="right" data-label="">
                      <a [routerLink]="['/accounts', a.id]" class="btn ghost small">View</a>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </section>
  `,
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
