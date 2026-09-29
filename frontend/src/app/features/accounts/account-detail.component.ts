import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountService } from '../../core/services/account.service';
import { TransactionService } from '../../core/services/transaction.service';
import { Account, AccountStatus } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { TransactionHistoryComponent } from '../transactions/transaction-history.component';
import { readError } from '../customers/customer-create.component';

const NEXT_ACTIONS: Record<AccountStatus, AccountStatus[]> = {
  ACTIVE: ['FROZEN', 'CLOSED'],
  FROZEN: ['ACTIVE', 'CLOSED'],
  CLOSED: [],
};

const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/;

function moneyForm(): FormGroup<{ amount: FormControl<string | null>; description: FormControl<string | null> }> {
  return new FormGroup({
    amount: new FormControl<string | null>(null, [
      Validators.required,
      Validators.min(0.01),
      Validators.pattern(AMOUNT_PATTERN),
    ]),
    description: new FormControl<string | null>(null, [Validators.maxLength(500)]),
  });
}

@Component({
  selector: 'app-account-detail',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, StatusBadgeComponent, TransactionHistoryComponent],
  template: `
    <section class="page">
      <a routerLink="/accounts" class="back">← All accounts</a>
      @if (loading()) {
        <div class="card"><p class="muted">Loading account…</p></div>
      } @else if (error()) {
        <div class="card error-card"><p>{{ error() }}</p></div>
      } @else if (account()) {
        <div class="hero">
          <div class="hero-main">
            <span class="overline">{{ account()!.accountType }} account</span>
            <p class="hero-amount">
              <span class="hero-currency">{{ account()!.currency }}</span>
              {{ account()!.balance }}
            </p>
            <span class="hero-meta">Available balance · {{ account()!.accountNumber }}</span>
          </div>
          <div class="hero-side">
            <h1>{{ account()!.accountNumber }}</h1>
            <app-status-badge [label]="account()!.status" [tone]="statusTone(account()!.status)" />
          </div>
        </div>

        <div class="grid">
          <div class="card">
            <h3>Details</h3>
            <dl>
              <div><dt>Account type</dt><dd>{{ account()!.accountType }}</dd></div>
              <div><dt>Customer</dt><dd><a [routerLink]="['/customers', account()!.customerId]">{{ account()!.customerNumber }}</a></dd></div>
              <div><dt>Created</dt><dd>{{ account()!.createdAt }}</dd></div>
              <div><dt>Updated</dt><dd>{{ account()!.updatedAt }}</dd></div>
            </dl>
          </div>
          <div class="card">
            <h3>Status</h3>
            <p class="muted">Controlled transitions only — CLOSED is terminal.</p>
            @if (nextActions().length === 0) {
              <p>This account is CLOSED. No further transitions are allowed.</p>
            } @else {
              <div class="row">
                @for (target of nextActions(); track target) {
                  <button type="button" class="btn ghost" (click)="changeStatus(target)">
                    {{ target === 'ACTIVE' ? 'Reactivate' : target === 'FROZEN' ? 'Freeze' : 'Close' }}
                  </button>
                }
              </div>
            }
            @if (actionMessage()) {
              <p class="action" role="status">{{ actionMessage() }}</p>
            }
          </div>
        </div>

        <div class="grid">
          <div class="card">
            <h3>Deposit</h3>
            <form [formGroup]="depositForm" (ngSubmit)="submitDeposit()">
              <label>Amount ({{ account()!.currency }})
                <input type="number" formControlName="amount" min="0.01" step="0.01" placeholder="1000.00" />
              </label>
              @if (depositForm.controls.amount.touched && depositForm.controls.amount.invalid) {
                <p class="field-error">Enter an amount greater than 0 (max 2 decimals).</p>
              }
              <label>Description (optional)
                <input type="text" formControlName="description" maxlength="500" placeholder="Cash deposit" />
              </label>
              <button type="submit" class="btn primary" [disabled]="depositForm.invalid || processing() !== null">
                {{ processing() === 'deposit' ? 'Depositing…' : 'Deposit' }}
              </button>
            </form>
          </div>
          <div class="card">
            <h3>Withdraw</h3>
            <form [formGroup]="withdrawForm" (ngSubmit)="submitWithdraw()">
              <label>Amount ({{ account()!.currency }})
                <input type="number" formControlName="amount" min="0.01" step="0.01" placeholder="500.00" />
              </label>
              @if (withdrawForm.controls.amount.touched && withdrawForm.controls.amount.invalid) {
                <p class="field-error">Enter an amount greater than 0 (max 2 decimals).</p>
              }
              <label>Description (optional)
                <input type="text" formControlName="description" maxlength="500" placeholder="ATM withdrawal" />
              </label>
              <button type="submit" class="btn primary" [disabled]="withdrawForm.invalid || processing() !== null">
                {{ processing() === 'withdraw' ? 'Withdrawing…' : 'Withdraw' }}
              </button>
            </form>
          </div>
        </div>
        @if (txnMessage()) {
          <p class="action" role="status">{{ txnMessage() }}</p>
        }
        @if (txnError()) {
          <div class="card error-card"><p>{{ txnError() }}</p></div>
        }

        <div class="card">
          <h3>Transaction history</h3>
          <app-transaction-history [transactions]="transactions()" [loading]="historyLoading()" />
        </div>
      }
    </section>
  `,
  styles: [
    `
      .hero {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1.5rem;
        flex-wrap: wrap;
        padding: 1.35rem 1.5rem;
        border-radius: var(--nx-r-lg);
        background: linear-gradient(135deg, #16233d 0%, #0f1b31 55%, #122a45 100%);
        color: #fff;
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
        align-items: center;
        gap: 0.75rem;
      }
      .hero-side h1 {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 650;
        letter-spacing: -0.012em;
        color: #fff;
      }
      .row {
        display: flex;
        gap: 0.5rem;
        flex-wrap: wrap;
      }
      .action {
        color: var(--nx-success);
        font-size: 0.8125rem;
        font-weight: 500;
      }
      @media (max-width: 640px) {
        .hero {
          padding: 1.15rem;
        }
        .hero-amount {
          font-size: 1.75rem;
        }
      }
    `,
  ],
})
export class AccountDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly accountsApi = inject(AccountService);
  private readonly transactionsApi = inject(TransactionService);

  readonly account = signal<Account | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly actionMessage = signal<string | null>(null);
  readonly transactions = signal<Transaction[]>([]);
  readonly historyLoading = signal(false);
  readonly processing = signal<'deposit' | 'withdraw' | null>(null);
  readonly txnMessage = signal<string | null>(null);
  readonly txnError = signal<string | null>(null);

  readonly depositForm = moneyForm();
  readonly withdrawForm = moneyForm();

  nextActions(): AccountStatus[] {
    const current = this.account()?.status;
    return current ? NEXT_ACTIONS[current] : [];
  }

  statusTone(status: AccountStatus): 'success' | 'warning' | 'neutral' {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'FROZEN':
        return 'warning';
      default:
        return 'neutral';
    }
  }

  ngOnInit(): void {
    // `Number(null)` is 0, so a missing or blank param has to be rejected
    // explicitly — otherwise the screen would quietly open account #0.
    const raw = this.route.snapshot.paramMap.get('id');
    const id = raw === null || raw.trim() === '' ? Number.NaN : Number(raw);
    if (!Number.isFinite(id)) {
      this.error.set('Invalid account id.');
      this.loading.set(false);
      return;
    }
    this.accountsApi.getById(id).subscribe({
      next: (a) => {
        this.account.set(a);
        this.loading.set(false);
        this.loadHistory(a.id);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 404 ? 'Account not found.' : readError(err));
        this.loading.set(false);
      },
    });
  }

  changeStatus(target: AccountStatus): void {
    const current = this.account();
    if (!current) {
      return;
    }
    this.accountsApi.changeStatus(current.id, target).subscribe({
      next: (updated) => {
        this.account.set(updated);
        this.actionMessage.set(`Account is now ${updated.status}.`);
      },
      error: (err: HttpErrorResponse) => this.actionMessage.set(readError(err)),
    });
  }

  submitDeposit(): void {
    if (this.depositForm.invalid || this.processing() !== null) {
      return;
    }
    const current = this.account();
    if (!current) {
      return;
    }
    this.processing.set('deposit');
    this.txnMessage.set(null);
    this.txnError.set(null);
    this.transactionsApi
      .deposit(current.id, Number(this.depositForm.value.amount), this.depositForm.value.description)
      .subscribe({
        next: (txn) => this.applyTransaction(txn, 'Deposit successful.'),
        error: (err: HttpErrorResponse) => this.failTransaction(err),
      });
  }

  submitWithdraw(): void {
    if (this.withdrawForm.invalid || this.processing() !== null) {
      return;
    }
    const current = this.account();
    if (!current) {
      return;
    }
    this.processing.set('withdraw');
    this.txnMessage.set(null);
    this.txnError.set(null);
    this.transactionsApi
      .withdraw(current.id, Number(this.withdrawForm.value.amount), this.withdrawForm.value.description)
      .subscribe({
        next: (txn) => this.applyTransaction(txn, 'Withdrawal successful.'),
        error: (err: HttpErrorResponse) => this.failTransaction(err),
      });
  }

  private loadHistory(accountId: number): void {
    this.historyLoading.set(true);
    this.transactionsApi.history(accountId, 0, 20).subscribe({
      next: (page) => {
        this.transactions.set(page.content);
        this.historyLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.txnError.set(readError(err));
        this.historyLoading.set(false);
      },
    });
  }

  /** Updates the visible balance in place — no full page refresh needed. */
  private applyTransaction(txn: Transaction, message: string): void {
    const current = this.account();
    if (current) {
      this.account.set({ ...current, balance: txn.balanceAfter });
    }
    this.transactions.update((list) => [txn, ...list]);
    this.depositForm.reset();
    this.withdrawForm.reset();
    this.txnMessage.set(`${message} New balance: ${txn.balanceAfter} ${txn.currency}.`);
    this.processing.set(null);
  }

  private failTransaction(err: HttpErrorResponse): void {
    // 422 (insufficient funds / inactive account) and other API errors
    // surface as clean messages; the balance shown stays untouched.
    this.txnError.set(readError(err));
    this.processing.set(null);
  }
}
