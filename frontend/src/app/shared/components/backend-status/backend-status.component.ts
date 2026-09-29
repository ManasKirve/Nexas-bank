import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HealthService, BackendConnectionState } from '../../../core/services/health.service';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';

/**
 * Small widget shown on the dashboard: "Data Source: LocalStorage".
 *
 * In the Docker build this reported whether the Spring Boot API was reachable.
 * In local demo mode there is no server, so it reports whether the browser
 * store is usable and seeded — the same three states, a new meaning.
 */
@Component({
  selector: 'app-backend-status',
  standalone: true,
  imports: [CommonModule, StatusBadgeComponent],
  template: `
    <div class="backend-status">
      <span class="label">Data Source:</span>
      @switch (state()) {
        @case ('checking') {
          <app-status-badge label="Checking..." tone="info" />
        }
        @case ('connected') {
          <app-status-badge label="LocalStorage" tone="success" />
        }
        @default {
          <app-status-badge label="Unavailable" tone="danger" />
        }
      }
      <button type="button" class="retry" (click)="refresh()">Retry</button>
    </div>
  `,
  styles: [
    `
      .backend-status {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.3rem 0.4rem 0.3rem 0.65rem;
        border: 1px solid var(--nx-border);
        border-radius: 999px;
        background: var(--nx-surface);
        box-shadow: var(--nx-shadow-xs);
      }
      .label {
        font-size: 0.6875rem;
        font-weight: 650;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--nx-muted);
        white-space: nowrap;
      }
      .retry {
        border: 1px solid var(--nx-border-2);
        background: var(--nx-surface);
        border-radius: 999px;
        padding: 0.2rem 0.6rem;
        cursor: pointer;
        font-size: 0.6875rem;
        font-weight: 650;
        color: var(--nx-ink-2);
        transition: background var(--nx-ease), color var(--nx-ease);
      }
      .retry:hover {
        background: var(--nx-surface-3);
        color: var(--nx-ink);
      }
    `,
  ],
})
export class BackendStatusComponent implements OnInit {
  private readonly health = inject(HealthService);
  readonly state = signal<BackendConnectionState>('checking');

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.state.set('checking');
    this.health.connectionState().subscribe((s) => this.state.set(s));
  }
}
