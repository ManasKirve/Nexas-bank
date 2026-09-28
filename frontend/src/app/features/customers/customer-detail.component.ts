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
        <div class="card"><p>Loading customer…</p></div>
      } @else if (error()) {
        <div class="card error"><p>{{ error() }}</p></div>
      } @else if (customer()) {
        <header class="page-head">
          <div>
            <h1>{{ customer()!.firstName }} {{ customer()!.lastName }}</h1>
            <p class="muted">{{ customer()!.customerNumber }} · {{ customer()!.email }}</p>
          </div>
          <app-status-badge [label]="customer()!.status" tone="info" />
        </header>
        <div class="grid">
          <div class="card">
            <h3>Details</h3>
            <dl>
              <div><dt>Phone</dt><dd>{{ customer()!.phone || '—' }}</dd></div>
              <div><dt>Created</dt><dd>{{ customer()!.createdAt }}</dd></div>
              <div><dt>Updated</dt><dd>{{ customer()!.updatedAt }}</dd></div>
            </dl>
            @if (actionMessage()) {
              <p class="action" role="status">{{ actionMessage() }}</p>
            }
            @if (customer()!.status !== 'INACTIVE') {
              <button type="button" class="btn danger" (click)="deactivate()">Deactivate customer</button>
            }
          </div>
          <div class="card">
            <h3>Accounts ({{ accounts().length }})</h3>
            @if (accounts().length === 0) {
              <p class="muted">No accounts yet.</p>
            } @else {
              <table>
                <thead><tr><th>Number</th><th>Type</th><th>Balance</th><th>Status</th></tr></thead>
                <tbody>
                  @for (a of accounts(); track a.id) {
                    <tr>
                      <td><a [routerLink]="['/accounts', a.id]">{{ a.accountNumber }}</a></td>
                      <td>{{ a.accountType }}</td>
                      <td>{{ a.balance }} {{ a.currency }}</td>
                      <td><app-status-badge [label]="a.status" tone="info" /></td>
                    </tr>
                  }
                </tbody>
              </table>
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
      .back { display: inline-block; margin-bottom: 1rem; color: #174ea6; }
      .page-head { display: flex; justify-content: space-between; align-items: center; gap: 1rem; margin-bottom: 1.25rem; flex-wrap: wrap; }
      h1 { margin: 0; }
      .muted { color: #5f6368; }
      .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1rem; }
      dl > div { display: flex; gap: 1rem; padding: 0.35rem 0; }
      dt { font-weight: 600; min-width: 90px; color: #5f6368; }
      dd { margin: 0; }
      table { width: 100%; border-collapse: collapse; margin-bottom: 0.75rem; }
      th, td { text-align: left; padding: 0.5rem; border-bottom: 1px solid #e8eaed; font-size: 0.9rem; }
      .btn { display: inline-block; text-decoration: none; padding: 0.55rem 1rem; border-radius: 8px; font-weight: 600; font-size: 0.9rem; border: 0; cursor: pointer; }
      .btn.primary { background: #174ea6; color: #fff; }
      .btn.danger { background: #fff; color: #a50e0e; border: 1px solid #f5b5b0; margin-top: 0.5rem; }
      .action { color: #137333; }
      .error { color: #a50e0e; }
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
}
