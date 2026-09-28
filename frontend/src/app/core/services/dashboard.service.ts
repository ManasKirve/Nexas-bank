import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { DashboardSummary } from '../../shared/models/dashboard.model';
import { DemoRepositoryService } from './demo-repository.service';

/**
 * Live aggregate counts for the dashboard, computed from the local demo store
 * instead of `GET /api/v1/dashboard/summary`.
 *
 * The contract still returns `DashboardSummary | null` so the component's
 * existing "no data yet" state keeps working; `null` is now only possible if
 * the dataset is genuinely empty.
 */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly repo = inject(DemoRepositoryService);

  getSummary(): Observable<DashboardSummary | null> {
    const accounts = this.repo.accounts();
    if (accounts.length === 0) {
      return of(null);
    }
    return of({
      totalCustomers: this.repo.customers().length,
      totalAccounts: accounts.length,
      activeAccounts: accounts.filter((a) => a.status === 'ACTIVE').length,
      frozenAccounts: accounts.filter((a) => a.status === 'FROZEN').length,
    });
  }
}
