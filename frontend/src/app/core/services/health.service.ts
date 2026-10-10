import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BackendHealth } from '../../shared/models/backend-health.model';

export type BackendConnectionState = 'checking' | 'connected' | 'unavailable';

/**
 * Connectivity probe against `GET /api/v1/health`.
 *
 * The endpoint is public, so this also works before sign-in. A failed request
 * maps to `unavailable` instead of erroring, which keeps the status widget's
 * three states unchanged.
 */
@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  checkHealth(): Observable<BackendHealth | null> {
    return this.http
      .get<BackendHealth>(`${this.base}/api/v1/health`)
      .pipe(catchError(() => of(null)));
  }

  connectionState(): Observable<BackendConnectionState> {
    return this.checkHealth().pipe(map((health) => (health ? 'connected' : 'unavailable')));
  }
}
