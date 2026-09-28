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
        <div class="card"><p>Loading customers…</p></div>
      } @else if (error()) {
        <div class="card error">
          <p>{{ error() }}</p>
          <button type="button" class="btn ghost" (click)="reload()">Retry</button>
        </div>
      } @else if (customers().length === 0) {
        <div class="card empty">
          <p>No customers yet. Create the first customer to get started.</p>
          <a routerLink="/customers/new" class="btn primary">Create customer</a>
        </div>
      } @else {
        <div class="card table-card">
          <table>
            <thead>
              <tr><th>Number</th><th>Name</th><th>Email</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              @for (c of customers(); track c.id) {
                <tr>
                  <td>{{ c.customerNumber }}</td>
                  <td>{{ c.firstName }} {{ c.lastName }}</td>
                  <td>{{ c.email }}</td>
                  <td><app-status-badge [label]="c.status" [tone]="tone(c.status)" /></td>
                  <td class="actions"><a [routerLink]="['/customers', c.id]">View</a></td>
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
