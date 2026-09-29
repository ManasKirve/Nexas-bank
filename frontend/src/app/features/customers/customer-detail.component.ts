import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { CustomerService } from '../../core/services/customer.service';
import { AccountService } from '../../core/services/account.service';
import { Customer } from '../../shared/models/customer.model';
import { Account } from '../../shared/models/account.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { readError } from './customer-create.component';

@Component({
  selector: 'app-customer-detail',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent],
  template: `
    <section class="page">
      <a routerLink="/customers" class="back">← All customers</a>
      @if (loading()) {
        <div class="card"><p class="muted">Loading customer…</p></div>
      } @else if (error()) {
        <div class="card error-card"><p>{{ error() }}</p></div>
      } @else if (customer()) {
        <header class="page-head">
          <div class="identity">
            <span class="identity-avatar" aria-hidden="true">
              {{ customer()!.firstName.slice(0, 1) }}{{ customer()!.lastName.slice(0, 1) }}
            </span>
            <div>
              <h1>{{ customer()!.firstName }} {{ customer()!.lastName }}</h1>
              <p class="muted">{{ customer()!.customerNumber }} · {{ customer()!.email }}</p>
            </div>
          </div>
          <app-status-badge [label]="customer()!.status" [tone]="statusTone(customer()!.status)" />
        </header>
        <div class="grid">
          <div class="card">
            <h3>Details</h3>
            <dl>
              <div><dt>Email</dt><dd>{{ customer()!.email }}</dd></div>
              <div><dt>Phone</dt><dd>{{ customer()!.phone || '—' }}</dd></div>
              <div><dt>Created</dt><dd>{{ customer()!.createdAt }}</dd></div>
              <div><dt>Updated</dt><dd>{{ customer()!.updatedAt }}</dd></div>
            </dl>
            @if (actionMessage()) {
              <p class="action" role="status">{{ actionMessage() }}</p>
            }
            @if (customer()!.status !== 'INACTIVE') {
              <button type="button" class="btn danger-quiet" (click)="deactivate()">Deactivate customer</button>
            }
          </div>
          <div class="card">
            <h3>Accounts ({{ accounts().length }})</h3>
            @if (accounts().length === 0) {
              <p class="muted">No accounts yet.</p>
            } @else {
              <div class="table-scroll">
                <table class="stackable">
                  <thead><tr><th>Number</th><th>Type</th><th class="num">Balance</th><th>Status</th></tr></thead>
                  <tbody>
                    @for (a of accounts(); track a.id) {
                      <tr>
                        <td data-label="Number"><a [routerLink]="['/accounts', a.id]"><code>{{ a.accountNumber }}</code></a></td>
                        <td data-label="Type" class="cell-strong">{{ a.accountType }}</td>
                        <td class="num cell-strong" data-label="Balance">{{ a.balance }} {{ a.currency }}</td>
                        <td data-label="Status"><app-status-badge [label]="a.status" [tone]="accountTone(a.status)" /></td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
            <a [routerLink]="['/accounts/new']" [queryParams]="{ customerId: customer()!.id }" class="btn primary">
              Open account
            </a>
          </div>
        </div>
      }
    </section>
  `,
  styles: [
    `
      .identity {
        display: flex;
        align-items: center;
        gap: 0.85rem;
        min-width: 0;
      }
      .identity-avatar {
        display: grid;
        place-items: center;
        width: 44px;
        height: 44px;
        flex: 0 0 44px;
        border-radius: 50%;
        background: var(--nx-accent-soft);
        color: var(--nx-accent);
        font-size: 0.9375rem;
        font-weight: 700;
        text-transform: uppercase;
      }
      .action {
        color: var(--nx-success);
        font-size: 0.8125rem;
        font-weight: 500;
      }
      .card > .btn {
        margin-top: 1rem;
      }
    `,
  ],
})
export class CustomerDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly customersApi = inject(CustomerService);
  private readonly accountsApi = inject(AccountService);
  readonly customer = signal<Customer | null>(null);
  readonly accounts = signal<Account[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly actionMessage = signal<string | null>(null);

  ngOnInit(): void {
    // `Number(null)` is 0, so a missing or blank param has to be rejected
    // explicitly — otherwise the screen would quietly open customer #0.
    const raw = this.route.snapshot.paramMap.get('id');
    const id = raw === null || raw.trim() === '' ? Number.NaN : Number(raw);
    if (!Number.isFinite(id)) {
      this.error.set('Invalid customer id.');
      this.loading.set(false);
      return;
    }
    this.customersApi.getById(id).subscribe({
      next: (c) => {
        this.customer.set(c);
        this.loading.set(false);
        this.accountsApi.getByCustomer(id).subscribe({
          next: (rows) => this.accounts.set(rows),
          error: () => this.actionMessage.set('Could not load accounts for this customer.'),
        });
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 404 ? 'Customer not found.' : readError(err));
        this.loading.set(false);
      },
    });
  }

  deactivate(): void {
    const current = this.customer();
    if (!current) {
      return;
    }
    this.customersApi.deactivate(current.id).subscribe({
      next: (updated) => {
        this.customer.set(updated);
        this.actionMessage.set(`Customer ${updated.customerNumber} deactivated.`);
      },
      error: (err: HttpErrorResponse) => this.actionMessage.set(readError(err)),
    });
  }

  statusTone(status: Customer['status']): 'success' | 'danger' | 'neutral' {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'BLOCKED':
        return 'danger';
      default:
        return 'neutral';
    }
  }

  accountTone(status: Account['status']): 'success' | 'warning' | 'neutral' {
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
