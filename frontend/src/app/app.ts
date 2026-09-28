import { Component, effect, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { DemoRepositoryService } from './core/services/demo-repository.service';

interface NavItem {
  path: string;
  label: string;
}

const ALL_NAV: NavItem[] = [
  { path: '/dashboard', label: 'Dashboard' },
  { path: '/accounts', label: 'Accounts' },
  { path: '/customers', label: 'Customers' },
  { path: '/transactions', label: 'Transactions' },
  { path: '/transfers', label: 'Transfers' },
  { path: '/fraud', label: 'Fraud' },
  { path: '/admin', label: 'Admin' },
];

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly appName = signal('NexaBank');
  protected readonly year = signal(new Date().getFullYear());
  protected readonly sidebarOpen = signal(true);

  private readonly repo = inject(DemoRepositoryService);

  /**
   * All screens are reachable — there is no role filtering in local demo mode.
   * Previously each item carried a `roles` list and was hidden from anyone
   * without the matching JWT claim.
   */
  protected readonly nav = ALL_NAV;

  /** Which demo customer the "my money" views are currently acting for. */
  protected readonly actingCustomer = signal('—');

  constructor() {
    // Re-read the acting customer whenever anything is written to LocalStorage.
    effect(() => {
      this.repo.storageRevision();
      this.actingCustomer.set(this.describeActingCustomer());
    });
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((v) => !v);
  }

  private describeActingCustomer(): string {
    const customer = this.repo.customers().find((c) => c.id === this.repo.activeCustomerId());
    return customer ? `${customer.firstName} ${customer.lastName} · ${customer.customerNumber}` : '—';
  }
}
