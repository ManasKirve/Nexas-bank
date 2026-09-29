import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
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
  imports: [BackendStatusComponent, StatusBadgeComponent, RouterLink, DecimalPipe, TransactionHistoryComponent],
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
        <div class="card"><p class="muted">Loading overview…</p></div>
      } @else if (summary() === null) {
        <div class="card empty">
          <p>Overview is empty — the demo dataset has not been seeded.</p>
          <p class="muted">Open Admin and use “Reset Demo Data”, or reload the page.</p>
          <button type="button" class="btn ghost" (click)="reload()">Retry</button>
        </div>
      } @else {
        <div class="grid cards">
          <article class="card kpi">
            <span class="kpi-icon tone-accent" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <path d="M15.5 20.5v-1.8a3.6 3.6 0 0 0-3.6-3.6H6.6A3.6 3.6 0 0 0 3 18.7v1.8M9.2 11.6a3.8 3.8 0 1 0 0-7.6 3.8 3.8 0 0 0 0 7.6ZM21 20.5v-1.8a3.6 3.6 0 0 0-2.7-3.5M15.6 4.1a3.6 3.6 0 0 1 0 7" />
              </svg>
            </span>
            <h3>Total Customers</h3>
            <p class="metric">{{ summary()!.totalCustomers | number }}</p>
            <a routerLink="/customers">Manage customers <span aria-hidden="true">→</span></a>
          </article>

          <article class="card kpi">
            <span class="kpi-icon tone-accent" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <path d="M2.5 6.5h19v11h-19zM2.5 10.5h19M6 14.5h3.5" />
              </svg>
            </span>
            <h3>Total Accounts</h3>
            <p class="metric">{{ summary()!.totalAccounts | number }}</p>
            <a routerLink="/accounts">Manage accounts <span aria-hidden="true">→</span></a>
          </article>

          <article class="card kpi">
            <span class="kpi-icon tone-success" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 6.5 9.5 17 4 11.5" />
              </svg>
            </span>
            <h3>Active Accounts</h3>
            <p class="metric">{{ summary()!.activeAccounts | number }}</p>
            <app-status-badge label="Active" tone="success" />
          </article>

          <article class="card kpi">
            <span class="kpi-icon tone-warning" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 3.5v17M4 7.5l8-4 8 4-8 4-8-4Z" />
              </svg>
            </span>
            <h3>Frozen Accounts</h3>
            <p class="metric">{{ summary()!.frozenAccounts | number }}</p>
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
          <div class="hero">
            <div class="hero-main">
              <span class="overline">Combined balance</span>
              <p class="hero-amount">
                <span class="hero-currency">INR</span>
                {{ totalBalance() | number: '1.0-2' }}
              </p>
              <span class="hero-meta">
                {{ myAccounts()?.length ?? 0 }} account(s) · acting demo customer
              </span>
            </div>
            <div class="hero-side">
              <div class="hero-stat">
                <span class="overline">Total deposits</span>
                <p class="hero-stat-value up">+{{ totalDeposits() | number: '1.0-2' }}</p>
              </div>
              <div class="hero-stat">
                <span class="overline">Total withdrawals</span>
                <p class="hero-stat-value down">−{{ totalWithdrawals() | number: '1.0-2' }}</p>
              </div>
            </div>
          </div>

          <div class="grid cards">
            <article class="mini">
              <h4>Combined balance</h4>
              <p class="metric sm">{{ totalBalance() | number: '1.0-2' }} INR</p>
            </article>
            <article class="mini">
              <h4>Total deposits</h4>
              <p class="metric sm up">+{{ totalDeposits() | number: '1.0-2' }} INR</p>
            </article>
            <article class="mini">
              <h4>Total withdrawals</h4>
              <p class="metric sm down">−{{ totalWithdrawals() | number: '1.0-2' }} INR</p>
            </article>
          </div>

          <h4 class="section-title">Recent transactions</h4>
          <app-transaction-history [transactions]="recentTransactions()" [loading]="financesLoading()" />

          @if (recentTransfers().length > 0) {
            <h4 class="section-title">Recent transfers</h4>
            <div class="grid cards">
              <article class="mini">
                <h4>Total sent</h4>
                <p class="metric sm down">−{{ totalSent() | number: '1.0-2' }} INR</p>
              </article>
              <article class="mini">
                <h4>Total received</h4>
                <p class="metric sm up">+{{ totalReceived() | number: '1.0-2' }} INR</p>
              </article>
            </div>
            <app-transaction-history [transactions]="recentTransfers()" [loading]="financesLoading()" />
          }
        }
      </div>

      <div class="card">
        <h3>Quick actions</h3>
        <div class="quick">
          <a routerLink="/transfers" class="quick-link">
            <span class="quick-icon tone-accent" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21.5 2.5 11 13M21.5 2.5l-6.7 19-3.6-8.7-8.7-3.6Z" />
              </svg>
            </span>
            <span class="quick-text">
              <span class="quick-title">Send a transfer</span>
              <span class="quick-meta">Pay a beneficiary</span>
            </span>
          </a>
          <a routerLink="/accounts/new" class="quick-link">
            <span class="quick-icon tone-accent" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <path d="M2.5 6.5h19v11h-19zM2.5 10.5h19M6 14.5h3.5" />
              </svg>
            </span>
            <span class="quick-text">
              <span class="quick-title">Open an account</span>
              <span class="quick-meta">Savings or checking</span>
            </span>
          </a>
          <a routerLink="/customers/new" class="quick-link">
            <span class="quick-icon tone-accent" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 5.5v13M5.5 12h13" />
              </svg>
            </span>
            <span class="quick-text">
              <span class="quick-title">Add a customer</span>
              <span class="quick-meta">Create a new profile</span>
            </span>
          </a>
          <a routerLink="/fraud" class="quick-link">
            <span class="quick-icon tone-danger" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 21.7s7.5-3.7 7.5-9.5V5.3L12 2.3 4.5 5.3v6.9c0 5.8 7.5 9.5 7.5 9.5Z" />
              </svg>
            </span>
            <span class="quick-text">
              <span class="quick-title">Fraud queue</span>
              <span class="quick-meta">Review held attempts</span>
            </span>
          </a>
        </div>
      </div>
    </section>
  `,
  styles: [
    `
      .kpi {
        display: grid;
        gap: 0.15rem;
        align-content: start;
        transition: box-shadow var(--nx-ease), transform var(--nx-ease), border-color var(--nx-ease);
      }
      .kpi:hover {
        box-shadow: var(--nx-shadow-md);
        border-color: var(--nx-border-2);
        transform: translateY(-1px);
      }
      .kpi h3 {
        margin: 0.55rem 0 0;
        color: var(--nx-muted);
        font-weight: 600;
      }
      .kpi .metric {
        margin: 0 0 0.5rem;
      }
      .kpi a {
        font-size: 0.75rem;
        font-weight: 600;
      }
      .kpi-icon,
      .quick-icon {
        display: grid;
        place-items: center;
        width: 32px;
        height: 32px;
        border-radius: var(--nx-r-md);
      }
      .kpi-icon svg,
      .quick-icon svg {
        width: 17px;
        height: 17px;
      }
      .tone-accent {
        background: var(--nx-accent-soft);
        color: var(--nx-accent);
      }
      .tone-success {
        background: var(--nx-success-soft);
        color: var(--nx-success);
      }
      .tone-warning {
        background: var(--nx-warning-soft);
        color: var(--nx-warning);
      }
      .tone-danger {
        background: var(--nx-danger-soft);
        color: var(--nx-danger);
      }

      .finances {
        overflow: hidden;
      }
      .hero {
        display: flex;
        align-items: stretch;
        justify-content: space-between;
        gap: 1.5rem;
        flex-wrap: wrap;
        margin-bottom: 1.25rem;
        padding: 1.35rem 1.5rem;
        border-radius: var(--nx-r-lg);
        background: linear-gradient(135deg, #16233d 0%, #0f1b31 55%, #122a45 100%);
        color: #fff;
      }
      .hero-main {
        min-width: 0;
      }
      .hero .overline {
        color: #93a6c8;
      }
      .hero-amount {
        display: flex;
        align-items: baseline;
        gap: 0.5rem;
        margin: 0.3rem 0 0.25rem;
        font-size: 2.25rem;
        font-weight: 700;
        letter-spacing: -0.03em;
        line-height: 1.1;
        font-variant-numeric: tabular-nums;
      }
      .hero-currency {
        font-size: 0.875rem;
        font-weight: 650;
        letter-spacing: 0.04em;
        color: #93a6c8;
      }
      .hero-meta {
        font-size: 0.75rem;
        color: #93a6c8;
      }
      .hero-side {
        display: flex;
        gap: 2rem;
      }
      .hero-stat {
        min-width: 0;
      }
      .hero-stat-value {
        margin-top: 0.3rem;
        font-size: 1.125rem;
        font-weight: 700;
        font-variant-numeric: tabular-nums;
        letter-spacing: -0.015em;
      }
      .hero .up {
        color: #6ee7b7;
      }
      .hero .down {
        color: #fda4a4;
      }
      .section-title {
        margin: 1.25rem 0 0.6rem;
        color: var(--nx-ink-2);
      }
      .mini {
        padding: 0.85rem 0.95rem;
        border: 1px solid var(--nx-border);
        border-radius: var(--nx-r-md);
        background: var(--nx-surface-2);
      }
      .mini h4 {
        margin: 0;
        color: var(--nx-muted);
        font-size: 0.75rem;
        font-weight: 600;
      }
      .mini .metric {
        margin-top: 0.3rem;
      }

      .quick {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
        gap: 0.75rem;
      }
      .quick-link {
        display: flex;
        align-items: center;
        gap: 0.7rem;
        padding: 0.75rem 0.85rem;
        border: 1px solid var(--nx-border);
        border-radius: var(--nx-r-md);
        background: var(--nx-surface);
        color: inherit;
        text-decoration: none;
        transition: background var(--nx-ease), border-color var(--nx-ease), transform var(--nx-ease),
          box-shadow var(--nx-ease);
      }
      .quick-link:hover {
        background: var(--nx-surface-2);
        border-color: var(--nx-accent-line);
        transform: translateY(-1px);
        box-shadow: var(--nx-shadow-sm);
        color: inherit;
      }
      .quick-text {
        display: grid;
        min-width: 0;
      }
      .quick-title {
        font-size: 0.8125rem;
        font-weight: 650;
        color: var(--nx-ink);
      }
      .quick-meta {
        font-size: 0.6875rem;
        color: var(--nx-muted);
      }

      @media (max-width: 640px) {
        .hero {
          padding: 1.15rem;
        }
        .hero-amount {
          font-size: 1.75rem;
        }
        .hero-side {
          gap: 1.5rem;
        }
      }
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
