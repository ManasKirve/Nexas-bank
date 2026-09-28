import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Reusable status badge (e.g. Active, Pending, Blocked, Connected).
 */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `<span class="badge" [ngClass]="tone()">{{ label() }}</span>`,
  styles: [
    `
      .badge {
        display: inline-block;
        padding: 0.2rem 0.65rem;
        border-radius: 999px;
        font-size: 0.75rem;
        font-weight: 600;
        letter-spacing: 0.02em;
      }
      .success { background: #e6f4ea; color: #137333; border: 1px solid #b7dfc2; }
      .warning { background: #fef7e0; color: #8a5a00; border: 1px solid #f3d98b; }
      .danger { background: #fce8e6; color: #a50e0e; border: 1px solid #f5b5b0; }
      .info { background: #e8f0fe; color: #174ea6; border: 1px solid #b9cdf5; }
      .neutral { background: #f1f3f4; color: #3c4043; border: 1px solid #dadce0; }
    `,
  ],
})
export class StatusBadgeComponent {
  label = input.required<string>();
  tone = input<'success' | 'warning' | 'danger' | 'info' | 'neutral'>('neutral');
}
