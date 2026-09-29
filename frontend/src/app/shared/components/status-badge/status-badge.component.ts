import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Reusable status badge (e.g. Active, Pending, Blocked, Connected).
 */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `<span class="badge" [ngClass]="tone()"><span class="dot" aria-hidden="true"></span>{{ label() }}</span>`,
  styles: [
    `
      .badge {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.15rem 0.55rem 0.15rem 0.45rem;
        border-radius: 999px;
        border: 1px solid transparent;
        font-size: 0.6875rem;
        font-weight: 650;
        letter-spacing: 0.02em;
        line-height: 1.5;
        white-space: nowrap;
        text-transform: capitalize;
      }
      .dot {
        width: 5px;
        height: 5px;
        flex: 0 0 5px;
        border-radius: 50%;
        background: currentColor;
      }
      .success { background: var(--nx-success-soft); color: var(--nx-success); border-color: var(--nx-success-line); }
      .warning { background: var(--nx-warning-soft); color: var(--nx-warning); border-color: var(--nx-warning-line); }
      .danger { background: var(--nx-danger-soft); color: var(--nx-danger); border-color: var(--nx-danger-line); }
      .info { background: var(--nx-info-soft); color: var(--nx-info); border-color: var(--nx-info-line); }
      .neutral { background: var(--nx-surface-3); color: var(--nx-ink-2); border-color: var(--nx-border); }
    `,
  ],
})
export class StatusBadgeComponent {
  label = input.required<string>();
  tone = input<'success' | 'warning' | 'danger' | 'info' | 'neutral'>('neutral');
}
