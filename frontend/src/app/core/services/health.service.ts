import { Injectable, inject } from '@angular/core';
import { Observable, map, of } from 'rxjs';
import { BackendHealth } from '../../shared/models/backend-health.model';
import { DemoRepositoryService } from './demo-repository.service';

export type BackendConnectionState = 'checking' | 'connected' | 'unavailable';

/**
 * Data-source probe, replacing the Phase 1 `GET /api/v1/health` call.
 *
 * There is no backend in local demo mode, so the "is it working?" question
 * becomes "is LocalStorage usable and is the dataset seeded?". The component
 * and its `connected` / `unavailable` states are unchanged — only the meaning
 * is now "LocalStorage ready" instead of "Spring Boot reachable".
 */
@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly repo = inject(DemoRepositoryService);

  /** `status: 'UP'` when the local store is ready, so existing checks still pass. */
  checkHealth(): Observable<BackendHealth | null> {
    if (!this.repo.storageAvailable() || !this.repo.isSeeded()) {
      return of(null);
    }
    return of({
      status: 'UP',
      service: 'NexaBank LocalStorage Demo',
      timestamp: new Date().toISOString(),
    });
  }

  connectionState(): Observable<BackendConnectionState> {
    return this.checkHealth().pipe(map((health) => (health ? 'connected' : 'unavailable')));
  }
}
