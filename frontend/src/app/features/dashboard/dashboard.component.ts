import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BackendStatusComponent } from '../../shared/components/backend-status/backend-status.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { DashboardService } from '../../core/services/dashboard.service';
import { AccountService } from '../../core/services/account.service';
import { TransactionService } from '../../core/services/transaction.service';
import { TransactionHistoryComponent } from '../transactions/transaction-history.component';
import { DashboardSummary } from '../../shared/models/dashboard.model';
import { Account } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [BackendStatusComponent, StatusBadgeComponent, RouterLink, TransactionHistoryComponent],
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <h1>Dashboard</h1>
          <p class="muted">Live overview from the local demo dataset.</p>
        </div>
        <app-backend-status />
      </header>

      @if (loading()) {
        <div class="card"><p>Loading overview…</p></div>
      } @else if (summary() === null) {
        <div class="card empty">
          <p>Overview is empty — the demo dataset has not been seeded.</p>
          <p class="muted">Open Admin and use “Reset Demo Data”, or reload the page.</p>
          <button type="button" class="btn ghost" (click)="reload()">Retry</button>
        </div>
      } @else {
        <div class="grid cards">
          <article class="card">
            <h3>Total Customers</h3>
            <p class="metric">{{ summary()!.totalCustomers }}</p>
            <a routerLink="/customers">Manage customers →</a>
          </article>
          <article class="card">
            <h3>Total Accounts</h3>
            <p class="metric">{{ summary()!.totalAccounts }}</p>
            <a routerLink="/accounts">Manage accounts →</a>
          </article>
          <article class="card">
            <h3>Active Accounts</h3>
            <p class="metric">{{ summary()!.activeAccounts }}</p>
            <app-status-badge label="Active" tone="success" />
          </article>
          <article class="card">
            <h3>Frozen Accounts</h3>
            <p class="metric">{{ summary()!.frozenAccounts }}</p>
            <app-status-badge label="Frozen" tone="warning" />
          </article>
        </div>
        @if (summary()!.totalCustomers === 0) {
          <div class="card empty">
            <p>No banking data yet. Create your first customer to populate this dashboard.</p>
            <a routerLink="/customers/new" class="btn primary">Create customer</a>
          </div>
        }
      }

      <div class="card finances">
        <h3>My finances</h3>
        @if (financesLoading()) {
          <p class="muted">Loading balances…</p>
        } @else {
          <div class="grid cards">
            <article class="mini">
              <h4>Combined balance</h4>
              <p class="metric">{{ totalBalance() }} INR</p>
            </article>
            <article class="mini">
              <h4>Total deposits</h4>
              <p class="metric up">+{{ totalDeposits() }} INR</p>
            </article>
            <article class="mini">
              <h4>Total withdrawals</h4>
              <p class="metric down">−{{ totalWithdrawals() }} INR</p>
            </article>
          </div>
          <h4>Recent transactions</h4>
          <app-transaction-history [transactions]="recentTransactions()" [loading]="financesLoading()" />
          @if (recentTransfers().length > 0) {
            <h4>Recent transfers</h4>
            <div class="grid cards">
              <article class="mini">
                <h4>Total sent</h4>
                <p class="metric down">−{{ totalSent() }} INR</p>
              </article>
              <article class="mini">
                <h4>Total received</h4>
                <p class="metric up">+{{ totalReceived() }} INR</p>
              </article>
            </div>
            <app-transaction-history [transactions]="recentTransfers()" [loading]="financesLoading()" />
          }
        }
      </div>
    </section>
  `,
  styles: [
    `
      .page-head { display: flex; justify-content: space-between; align-items: center; gap: 1rem; margin-bottom: 1.25rem; flex-wrap: wrap; }
      h1 { margin: 0; font-size: 1.6rem; }
      .muted { color: #5f6368; margin: 0.25rem 0 0; }
      .grid.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1.25rem; }
      .metric { font-size: 1.5rem; font-weight: 700; margin: 0.35rem 0 0.6rem; }
      .card a { color: #174ea6; }
      .empty { display: grid; gap: 0.75rem; justify-items: start; }
      .btn { text-decoration: none; padding: 0.55rem 1rem; border-radius: 8px; font-weight: 600; font-size: 0.9rem; border: 0; cursor: pointer; }
      .btn.primary { background: #174ea6; color: #fff; }
      .btn.ghost { border: 1px solid #2f6fed; color: #2f6fed; background: #fff; }
      .finances { margin-top: 1.25rem; overflow-x: auto; }
      .mini h4 { margin: 0; color: #5f6368; font-size: 0.85rem; }
      .mini .metric { font-size: 1.25rem; }
      .up { color: #137333; }
      .down { color: #a50e0e; }
      h4 { margin: 1rem 0 0.5rem; }
    `,
  ],
})
export class DashboardComponent implements OnInit {
  private readonly dashboardApi = inject(DashboardService);
  private readonly accountsApi = inject(AccountService);
  private readonly transactionsApi = inject(TransactionService);

  readonly summary = signal<DashboardSummary | null | undefined>(undefined);
  readonly loading = signal(true);
  readonly myAccounts = signal<Account[] | null>(null);
  readonly myTransactions = signal<Transaction[]>([]);
  readonly financesLoading = signal(false);

  readonly totalBalance = computed(() =>
    (this.myAccounts() ?? []).reduce((sum, a) => sum + Number(a.balance), 0),
  );
  readonly totalDeposits = computed(() => this.sumByType('DEPOSIT'));
  readonly totalWithdrawals = computed(() => this.sumByType('WITHDRAWAL'));
  readonly recentTransactions = computed(() => this.myTransactions().slice(0, 5));
  readonly recentTransfers = computed(() =>
    this.myTransactions()
      .filter((t) => t.transactionType === 'TRANSFER_DEBIT' || t.transactionType === 'TRANSFER_CREDIT')
      .slice(0, 5),
  );
  readonly totalSent = computed(() => this.sumByType('TRANSFER_DEBIT'));
  readonly totalReceived = computed(() => this.sumByType('TRANSFER_CREDIT'));

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.dashboardApi.getSummary().subscribe((result) => {
      this.summary.set(result);
      this.loading.set(false);
    });
    // One bounded query per resource; totals derive client-side from the page.
    // "My money" is always the acting demo customer chosen in Admin.
    this.financesLoading.set(true);
    this.accountsApi.getMyAccounts().subscribe((accounts) => this.myAccounts.set(accounts));
    this.transactionsApi.ownHistory(0, 50).subscribe((page) => {
      this.myTransactions.set(page.content);
      this.financesLoading.set(false);
    });
  }

  private sumByType(type: Transaction['transactionType']): number {
    return this.myTransactions()
      .filter((t) => t.transactionType === type && t.status === 'COMPLETED')
      .reduce((sum, t) => sum + Number(t.amount), 0);
  }
}
