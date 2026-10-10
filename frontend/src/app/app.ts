import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { Role } from './shared/models/auth.model';

interface NavItem {
  path: string;
  label: string;
  /** Single-path 24×24 line icon, drawn with `currentColor` so it inherits state. */
  icon: string;
  /** Roles allowed to see the item; empty means any authenticated user. */
  roles: Role[];
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const STAFF: Role[] = ['BANK_EMPLOYEE', 'ADMIN'];
const MONEY_MOVERS: Role[] = ['CUSTOMER', 'BANK_EMPLOYEE', 'ADMIN'];
const FRAUD_ROLES: Role[] = ['FRAUD_ANALYST', 'ADMIN'];

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    items: [
      {
        path: '/dashboard',
        label: 'Dashboard',
        icon: 'M3.5 3.5h6v6h-6zM14.5 3.5h6v4.5h-6zM14.5 12h6v8.5h-6zM3.5 13h6v7.5h-6z',
        roles: [],
      },
    ],
  },
  {
    label: 'Banking',
    items: [
      {
        path: '/accounts',
        label: 'Accounts',
        icon: 'M2.5 6.5h19v11h-19zM2.5 10.5h19M6 14.5h3.5',
        roles: STAFF,
      },
      {
        path: '/customers',
        label: 'Customers',
        icon: 'M15.5 20.5v-1.8a3.6 3.6 0 0 0-3.6-3.6H6.6A3.6 3.6 0 0 0 3 18.7v1.8M9.2 11.6a3.8 3.8 0 1 0 0-7.6 3.8 3.8 0 0 0 0 7.6ZM21 20.5v-1.8a3.6 3.6 0 0 0-2.7-3.5M15.6 4.1a3.6 3.6 0 0 1 0 7',
        roles: STAFF,
      },
    ],
  },
  {
    label: 'Money movement',
    items: [
      {
        path: '/transactions',
        label: 'Transactions',
        icon: 'M4 6.5h16M4 12h16M4 17.5h9',
        roles: MONEY_MOVERS,
      },
      {
        path: '/transfers',
        label: 'Transfers',
        icon: 'M21.5 2.5 11 13M21.5 2.5l-6.7 19-3.6-8.7-8.7-3.6Z',
        roles: MONEY_MOVERS,
      },
    ],
  },
  {
    label: 'Risk & operations',
    items: [
      {
        path: '/fraud',
        label: 'Fraud',
        icon: 'M12 21.7s7.5-3.7 7.5-9.5V5.3L12 2.3 4.5 5.3v6.9c0 5.8 7.5 9.5 7.5 9.5Z',
        roles: FRAUD_ROLES,
      },
      {
        path: '/admin',
        label: 'Admin',
        icon: 'M3.5 6.5h9M17.5 6.5h3M3.5 12h3M11.5 12h9M3.5 17.5h9M17.5 17.5h3M15 4v5M9.5 9.5v5M15 15v5',
        roles: ['ADMIN'],
      },
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
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly appName = signal('NexaBank');
  protected readonly year = signal(new Date().getFullYear());
  protected readonly sidebarOpen = signal(true);

  /** Mobile-only drawer state; ignored by the desktop layout. */
  protected readonly drawerOpen = signal(false);

  readonly isAuthenticated = this.auth.isAuthenticated;
  readonly user = this.auth.user;
  readonly role = this.auth.role;

  /** Navigation filtered to the items the signed-in role may reach. */
  protected readonly navGroups = computed<NavGroup[]>(() =>
    NAV_GROUPS.map((group) => ({
      label: group.label,
      items: group.items.filter(
        (item) => item.roles.length === 0 || this.auth.hasAnyRole(...item.roles),
      ),
    })).filter((group) => group.items.length > 0),
  );

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

  logout(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }

  /** Defensive because the spec environment may not implement `matchMedia`. */
  private matchesCompact(): boolean {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return false;
    }
    return window.matchMedia(COMPACT_QUERY).matches;
  }
}
