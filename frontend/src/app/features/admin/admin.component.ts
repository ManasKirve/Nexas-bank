import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Customer } from '../../shared/models/customer.model';
import { DemoActivityEntry } from '../../shared/models/audit.model';
import { DemoRepositoryService } from '../../core/services/demo-repository.service';
import { FraudRuleEngine } from '../../core/services/fraud-rule-engine.service';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';

interface StorageCounts {
  customers: number;
  accounts: number;
  transactions: number;
  beneficiaries: number;
  fraudAlerts: number;
  fraudEvaluations: number;
}

/**
 * Admin screen for the local demo.
 *
 * Two things live here that authentication used to provide:
 *
 * - the **acting demo customer** selector, which decides whose money the
 *   "my accounts / my transactions / beneficiaries" screens show;
 * - the **Reset Demo Data** action, which wipes every `nexabank_*` key and
 *   re-seeds the realistic dataset from scratch. It asks for confirmation
 *   first, because it discards everything done during the session.
 *
 * It also exposes the storage footprint and the fraud rule set so the demo is
 * self-explanatory.
 */
@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [StatusBadgeComponent, DatePipe],
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <h1>Admin</h1>
          <p class="muted">Local demo settings — no backend, no accounts, no passwords.</p>
        </div>
      </header>

      <div class="card">
        <h3>Acting demo customer</h3>
        <p class="muted">
          Every “my …” screen acts on behalf of this customer. This replaces the JWT
          identity the Docker build derived from the signed-in user.
        </p>
        <label class="select-label">
          <span class="visually-hidden">Acting customer</span>
          <select
            [value]="activeCustomerId()"
            (change)="selectCustomer($event)"
            aria-label="Acting demo customer"
          >
            @for (c of customers(); track c.id) {
              <option [value]="c.id">{{ c.firstName }} {{ c.lastName }} · {{ c.customerNumber }}</option>
            }
          </select>
        </label>
        @if (activeCustomer()) {
          <p class="note">
            {{ activeCustomer()!.email }} · {{ accountCount() }} account(s)
          </p>
        }
      </div>

      <div class="card">
        <h3>Storage</h3>
        <p class="muted">
          Data source: <app-status-badge label="LocalStorage" tone="success" /> ·
          approximately {{ sizeKb() }} KB
        </p>
        @if (!storageAvailable()) {
          <p class="form-error">
            LocalStorage is unavailable (private mode or storage disabled). The demo keeps
            running but nothing will persist.
          </p>
        }
        <dl class="counts">
          <div><dt>Customers</dt><dd>{{ counts().customers }}</dd></div>
          <div><dt>Accounts</dt><dd>{{ counts().accounts }}</dd></div>
          <div><dt>Transactions</dt><dd>{{ counts().transactions }}</dd></div>
          <div><dt>Beneficiaries</dt><dd>{{ counts().beneficiaries }}</dd></div>
          <div><dt>Fraud alerts</dt><dd>{{ counts().fraudAlerts }}</dd></div>
          <div><dt>Fraud evaluations</dt><dd>{{ counts().fraudEvaluations }}</dd></div>
        </dl>
      </div>

      <div class="card">
        <h3>Reset demo data</h3>
        <p class="muted">
          Deletes every stored record and rebuilds the seeded dataset. Anything you created
          in this session is lost.
        </p>
        @if (!confirmingReset()) {
          <button type="button" class="btn danger" (click)="confirmingReset.set(true)">
            Reset Demo Data
          </button>
        } @else {
          <div class="confirm">
            <p class="cell-strong">Are you sure? This cannot be undone.</p>
            <button type="button" class="btn danger" (click)="reset()">Yes, reset everything</button>
            <button type="button" class="btn ghost" (click)="confirmingReset.set(false)">Cancel</button>
          </div>
        }
        @if (resetMessage()) {
          <p class="action" role="status">{{ resetMessage() }}</p>
        }
      </div>

      @if (activity().length > 0) {
        <div class="card">
          <h3>Recent demo activity</h3>
          <ul class="activity">
            @for (entry of activity(); track entry.id) {
              <li>
                <span class="type">{{ entry.type }}</span>
                <span class="summary">{{ entry.summary }}</span>
                <span class="muted at">{{ entry.at | date: 'medium' }}</span>
              </li>
            }
          </ul>
        </div>
      }

      <div class="card">
        <h3>Fraud rules in effect</h3>
        <p class="muted">
          REVIEW at {{ config.reviewThreshold }} · BLOCK at {{ config.blockThreshold }} ·
          evaluation version {{ config.evaluationVersion }}
        </p>
        <ul class="rules">
          @for (rule of rules; track rule.code) {
            <li><code>{{ rule.code }}</code> — {{ rule.description }} <span class="muted">(+{{ rule.score }})</span></li>
          }
        </ul>
      </div>
    </section>
  `,
  styles: [
    `
      .select-label {
        max-width: 340px;
        margin-top: 0.5rem;
      }
      .counts {
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 0.5rem;
        margin-top: 0.85rem;
      }
      .counts > div {
        flex-direction: column;
        align-items: flex-start;
        gap: 0.15rem;
        padding: 0.6rem 0.75rem;
        border: 1px solid var(--nx-border);
        border-radius: var(--nx-r-md);
        background: var(--nx-surface-2);
        border-bottom: 1px solid var(--nx-border);
      }
      .counts dt {
        min-width: 0;
        font-size: 0.6875rem;
        font-weight: 650;
        letter-spacing: 0.06em;
        text-transform: uppercase;
      }
      .counts dd {
        font-size: 1.125rem;
        font-variant-numeric: tabular-nums;
      }
      .confirm {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        flex-wrap: wrap;
      }
      .confirm p {
        margin: 0 0.25rem 0 0;
      }
      .action {
        margin-top: 0.75rem;
        color: var(--nx-success);
        font-size: 0.8125rem;
        font-weight: 500;
      }
      .form-error {
        margin-top: 0.75rem;
      }
      .activity {
        list-style: none;
        display: grid;
        gap: 0.15rem;
      }
      .activity li {
        display: grid;
        grid-template-columns: 150px 1fr auto;
        gap: 0.75rem;
        align-items: baseline;
        padding: 0.5rem 0;
        border-bottom: 1px solid var(--nx-border);
      }
      .activity li:last-child {
        border-bottom: 0;
      }
      .summary {
        min-width: 0;
        overflow-wrap: anywhere;
      }
      .type {
        font-family: var(--nx-mono);
        font-size: 0.6875rem;
        font-weight: 600;
        color: var(--nx-accent);
      }
      .at {
        font-size: 0.75rem;
        white-space: nowrap;
      }
      .rules {
        margin: 0.5rem 0 0;
        padding-left: 1.15rem;
        display: grid;
        gap: 0.4rem;
        font-size: 0.8125rem;
      }
      @media (max-width: 640px) {
        .activity li {
          grid-template-columns: 1fr;
          gap: 0.15rem;
        }
      }
    `,
  ],
})
export class AdminComponent implements OnInit {
  private readonly repo = inject(DemoRepositoryService);
  private readonly engine = inject(FraudRuleEngine);

  readonly customers = signal<Customer[]>([]);
  readonly activeCustomerId = signal<number>(0);
  readonly counts = signal<StorageCounts>({
    customers: 0,
    accounts: 0,
    transactions: 0,
    beneficiaries: 0,
    fraudAlerts: 0,
    fraudEvaluations: 0,
  });
  readonly activity = signal<DemoActivityEntry[]>([]);
  readonly sizeKb = signal(0);
  readonly storageAvailable = signal(true);
  readonly confirmingReset = signal(false);
  readonly resetMessage = signal<string | null>(null);

  readonly rules = this.engine.describeRules();
  readonly config = this.engine.config;

  readonly activeCustomer = signal<Customer | null>(null);
  readonly accountCount = signal(0);

  ngOnInit(): void {
    this.refresh();
  }

  selectCustomer(event: Event): void {
    const id = Number((event.target as HTMLSelectElement).value);
    this.repo.setActiveCustomerId(id);
    this.refresh();
  }

  reset(): void {
    this.confirmingReset.set(false);
    this.repo.reset();
    this.resetMessage.set('Demo data reset to the seeded starting state.');
    this.refresh();
  }

  private refresh(): void {
    const customers = this.repo.customers();
    const activeId = this.repo.activeCustomerId();
    this.customers.set(customers);
    this.activeCustomerId.set(activeId);
    this.activeCustomer.set(customers.find((c) => c.id === activeId) ?? null);
    this.accountCount.set(this.repo.accounts().filter((a) => a.customerId === activeId).length);
    this.counts.set(this.repo.counts());
    this.activity.set(this.repo.activity(20));
    this.sizeKb.set(this.repo.sizeKb());
    this.storageAvailable.set(this.repo.storageAvailable());
  }
}
