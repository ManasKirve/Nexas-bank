import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CustomerService } from '../../core/services/customer.service';
import { Customer } from '../../shared/models/customer.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'app-customer-list',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent],
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <h1>Customers</h1>
          <p class="muted">Customer directory backed by the NexaBank API.</p>
        </div>
        <a routerLink="/customers/new" class="btn primary">New customer</a>
      </header>

      @if (loading()) {
        <div class="card"><p class="muted">Loading customers…</p></div>
      } @else if (error()) {
        <div class="card error-card">
          <p>{{ error() }}</p>
          <button type="button" class="btn ghost" (click)="reload()">Retry</button>
        </div>
      } @else if (customers().length === 0) {
        <div class="card empty">
          <p>No customers yet. Create the first customer to get started.</p>
          <a routerLink="/customers/new" class="btn primary">Create customer</a>
        </div>
      } @else {
        <div class="card">
          <div class="table-scroll">
            <table class="stackable">
              <thead>
                <tr><th>Number</th><th>Name</th><th>Email</th><th>Status</th><th class="right"></th></tr>
              </thead>
              <tbody>
                @for (c of customers(); track c.id) {
                  <tr>
                    <td data-label="Number"><code>{{ c.customerNumber }}</code></td>
                    <td data-label="Name">
                      <span class="name-cell">
                        <span class="name-avatar" aria-hidden="true">{{ c.firstName.slice(0, 1) }}{{ c.lastName.slice(0, 1) }}</span>
                        <span class="cell-strong">{{ c.firstName }} {{ c.lastName }}</span>
                      </span>
                    </td>
                    <td data-label="Email" class="muted">{{ c.email }}</td>
                    <td data-label="Status"><app-status-badge [label]="c.status" [tone]="tone(c.status)" /></td>
                    <td class="right" data-label="">
                      <a [routerLink]="['/customers', c.id]" class="btn ghost small">View</a>
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
  styles: [
    `
      .name-cell {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        white-space: nowrap;
      }
      .name-avatar {
        display: grid;
        place-items: center;
        width: 28px;
        height: 28px;
        flex: 0 0 28px;
        border-radius: 50%;
        background: var(--nx-accent-soft);
        color: var(--nx-accent);
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
      }
    `,
  ],
})
export class CustomerListComponent implements OnInit {
  private readonly customersApi = inject(CustomerService);
  readonly customers = signal<Customer[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.customersApi.getAll().subscribe({
      next: (rows) => {
        this.customers.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load customers. Is the backend running?');
        this.loading.set(false);
      },
    });
  }

  tone(status: Customer['status']): 'success' | 'warning' | 'danger' | 'neutral' {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'BLOCKED':
        return 'danger';
      default:
        return 'neutral';
    }
  }
}
