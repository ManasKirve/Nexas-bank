import { Component, effect, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { DemoRepositoryService } from './core/services/demo-repository.service';

interface NavItem {
  path: string;
  label: string;
  /** Single-path 24×24 line icon, drawn with `currentColor` so it inherits state. */
  icon: string;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    items: [{ path: '/dashboard', label: 'Dashboard', icon: 'M3.5 3.5h6v6h-6zM14.5 3.5h6v4.5h-6zM14.5 12h6v8.5h-6zM3.5 13h6v7.5h-6z' }],
  },
  {
    label: 'Banking',
    items: [
      { path: '/accounts', label: 'Accounts', icon: 'M2.5 6.5h19v11h-19zM2.5 10.5h19M6 14.5h3.5' },
      { path: '/customers', label: 'Customers', icon: 'M15.5 20.5v-1.8a3.6 3.6 0 0 0-3.6-3.6H6.6A3.6 3.6 0 0 0 3 18.7v1.8M9.2 11.6a3.8 3.8 0 1 0 0-7.6 3.8 3.8 0 0 0 0 7.6ZM21 20.5v-1.8a3.6 3.6 0 0 0-2.7-3.5M15.6 4.1a3.6 3.6 0 0 1 0 7' },
    ],
  },
  {
    label: 'Money movement',
    items: [
      { path: '/transactions', label: 'Transactions', icon: 'M4 6.5h16M4 12h16M4 17.5h9' },
      { path: '/transfers', label: 'Transfers', icon: 'M21.5 2.5 11 13M21.5 2.5l-6.7 19-3.6-8.7-8.7-3.6Z' },
    ],
  },
  {
    label: 'Risk & operations',
    items: [
      { path: '/fraud', label: 'Fraud', icon: 'M12 21.7s7.5-3.7 7.5-9.5V5.3L12 2.3 4.5 5.3v6.9c0 5.8 7.5 9.5 7.5 9.5Z' },
      { path: '/admin', label: 'Admin', icon: 'M3.5 6.5h9M17.5 6.5h3M3.5 12h3M11.5 12h9M3.5 17.5h9M17.5 17.5h3M15 4v5M9.5 9.5v5M15 15v5' },
    ],
  },
];

const COMPACT_QUERY = '(max-width: 1024px)';

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

  /** Mobile-only drawer state; ignored by the desktop layout. */
  protected readonly drawerOpen = signal(false);

  private readonly repo = inject(DemoRepositoryService);

  /**
   * All screens are reachable — there is no role filtering in local demo mode.
   * Previously each item carried a `roles` list and was hidden from anyone
   * without the matching JWT claim. The seven routes are unchanged; they are
   * only grouped into labelled sections for the sidebar.
   */
  protected readonly navGroups = NAV_GROUPS;

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
    // Queried at click time rather than cached, so the button keeps behaving
    // correctly when the window crosses the compact breakpoint without a reload.
    if (this.matchesCompact()) {
      this.drawerOpen.update((v) => !v);
      return;
    }
    this.sidebarOpen.update((v) => !v);
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  /** Defensive because the spec environment may not implement `matchMedia`. */
  private matchesCompact(): boolean {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return false;
    }
    return window.matchMedia(COMPACT_QUERY).matches;
  }

  private describeActingCustomer(): string {
    const customer = this.repo.customers().find((c) => c.id === this.repo.activeCustomerId());
    return customer ? `${customer.firstName} ${customer.lastName} · ${customer.customerNumber}` : '—';
  }
}
