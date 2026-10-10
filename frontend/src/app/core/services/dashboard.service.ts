import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardSummary } from '../../shared/models/dashboard.model';

/**
 * Dashboard aggregates API client (`GET /api/v1/dashboard/summary`).
 *
 * Every figure comes from the database; the UI never hardcodes banking numbers.
 */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/api/v1/dashboard/summary`);
  }
}
