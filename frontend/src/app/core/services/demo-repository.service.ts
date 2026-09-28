import { Injectable, inject } from '@angular/core';
import { Account } from '../../shared/models/account.model';
import { Beneficiary } from '../../shared/models/beneficiary.model';
import { Customer } from '../../shared/models/customer.model';
import { FraudAlert, FraudEvaluation } from '../../shared/models/fraud.model';
import { Transaction } from '../../shared/models/transaction.model';
import { DemoActivityEntry, DemoActivityType } from '../../shared/models/audit.model';
import { buildDemoDataset } from '../data/demo-data';
import { FraudRuleEngine } from './fraud-rule-engine.service';
import { LocalStorageService, STORAGE_KEYS, DEMO_SCHEMA_VERSION } from './local-storage.service';

/**
 * The demo repository: every read and write of persisted NexaBank data goes
 * through here.
 *
 * Feature services (`CustomerService`, `AccountService`, `FraudService`, …)
 * depend on this instead of touching LocalStorage or `HttpClient` directly.
 * Responsibilities:
 *
 * - seed the realistic dataset once, on the first launch only;
 * - expose typed collection accessors that always return fresh copies;
 * - own the "which demo customer am I acting as" selection that used to come
 *   from the JWT;
 * - keep the demo activity trail;
 * - reset everything on demand.
 */
@Injectable({ providedIn: 'root' })
export class DemoRepositoryService {
  private readonly storage = inject(LocalStorageService);
  private readonly engine = inject(FraudRuleEngine);
  private readonly schemaVersion = String(DEMO_SCHEMA_VERSION);

  /**
   * Seeds the dataset on the very first launch. Safe to call on every start:
   * existing data is never touched, so a page refresh or a browser restart
   * keeps whatever the user did.
   */
  initialize(): void {
    if (this.storage.isSeeded()) {
      return;
    }
    this.seed();
  }

  /** Wipes the stored demo data and rebuilds it from scratch. */
  reset(): void {
    this.storage.clearAll();
    this.seed();
  }

  private seed(): void {
    const dataset = buildDemoDataset(this.engine);
    this.storage.write(STORAGE_KEYS.customers, dataset.customers);
    this.storage.write(STORAGE_KEYS.accounts, dataset.accounts);
    this.storage.write(STORAGE_KEYS.transactions, dataset.transactions);
    this.storage.write(STORAGE_KEYS.beneficiaries, dataset.beneficiaries);
    this.storage.write(STORAGE_KEYS.fraudAlerts, dataset.fraudAlerts);
    this.storage.write(STORAGE_KEYS.fraudEvaluations, dataset.fraudEvaluations);
    this.storage.write(STORAGE_KEYS.auditLog, dataset.auditLog);
    this.storage.writeScalar(STORAGE_KEYS.activeCustomerId, String(dataset.activeCustomerId));
    // Written last: the marker proves every collection above is present.
    this.storage.writeScalar(STORAGE_KEYS.version, this.schemaVersion);
    this.storage.writeScalar(STORAGE_KEYS.marker, 'true');
  }

  // ---------- customers ----------

  customers(): Customer[] {
    return this.storage.readList<Customer>(STORAGE_KEYS.customers);
  }

  saveCustomers(rows: Customer[]): void {
    this.storage.write(STORAGE_KEYS.customers, rows);
  }

  // ---------- accounts ----------

  accounts(): Account[] {
    return this.storage.readList<Account>(STORAGE_KEYS.accounts);
  }

  saveAccounts(rows: Account[]): void {
    this.storage.write(STORAGE_KEYS.accounts, rows);
  }

  // ---------- transactions ----------

  transactions(): Transaction[] {
    return this.storage.readList<Transaction>(STORAGE_KEYS.transactions);
  }

  saveTransactions(rows: Transaction[]): void {
    this.storage.write(STORAGE_KEYS.transactions, rows);
  }

  // ---------- beneficiaries ----------

  beneficiaries(): Beneficiary[] {
    return this.storage.readList<Beneficiary>(STORAGE_KEYS.beneficiaries);
  }

  saveBeneficiaries(rows: Beneficiary[]): void {
    this.storage.write(STORAGE_KEYS.beneficiaries, rows);
  }

  // ---------- fraud ----------

  fraudAlerts(): FraudAlert[] {
    return this.storage.readList<FraudAlert>(STORAGE_KEYS.fraudAlerts);
  }

  saveFraudAlerts(rows: FraudAlert[]): void {
    this.storage.write(STORAGE_KEYS.fraudAlerts, rows);
  }

  fraudEvaluations(): FraudEvaluation[] {
    return this.storage.readList<FraudEvaluation>(STORAGE_KEYS.fraudEvaluations);
  }

  saveFraudEvaluations(rows: FraudEvaluation[]): void {
    this.storage.write(STORAGE_KEYS.fraudEvaluations, rows);
  }

  // ---------- demo activity trail ----------

  activity(limit = 50): DemoActivityEntry[] {
    return this.storage.readList<DemoActivityEntry>(STORAGE_KEYS.auditLog).slice(0, limit);
  }

  /** Appends one entry, newest first, bounded so storage never grows forever. */
  appendActivity(
    type: DemoActivityType,
    summary: string,
    reference: string | null = null,
    details: string | null = null,
  ): void {
    const existing = this.storage.readList<DemoActivityEntry>(STORAGE_KEYS.auditLog);
    const entry: DemoActivityEntry = {
      id: (existing[0]?.id ?? 0) + 1,
      at: new Date().toISOString(),
      type,
      summary,
      reference,
      details,
    };
    this.storage.write(STORAGE_KEYS.auditLog, [entry, ...existing].slice(0, 200));
  }

  // ---------- acting demo customer ----------

  /**
   * Which customer the "my money" views act for. In the JWT build this was
   * derived from the access token; in the local demo it is an explicit,
   * user-selectable choice (see the Admin screen) — not a session.
   */
  activeCustomerId(): number {
    const raw = Number(this.storage.readScalar(STORAGE_KEYS.activeCustomerId));
    if (Number.isFinite(raw) && this.customers().some((c) => c.id === raw)) {
      return raw;
    }
    const fallback = this.customers()[0];
    return fallback ? fallback.id : 1;
  }

  setActiveCustomerId(id: number): void {
    this.storage.writeScalar(STORAGE_KEYS.activeCustomerId, String(id));
  }

  // ---------- diagnostics ----------

  /**
   * Read by an `effect()` so screens refresh after a write performed anywhere
   * else, instead of each one polling.
   */
  storageRevision(): number {
    return this.storage.revisionNumber();
  }

  isSeeded(): boolean {
    return this.storage.isSeeded();
  }

  storageAvailable(): boolean {
    return this.storage.available;
  }

  sizeKb(): number {
    return this.storage.approximateSizeKb();
  }

  counts(): {
    customers: number;
    accounts: number;
    transactions: number;
    beneficiaries: number;
    fraudAlerts: number;
    fraudEvaluations: number;
  } {
    return {
      customers: this.customers().length,
      accounts: this.accounts().length,
      transactions: this.transactions().length,
      beneficiaries: this.beneficiaries().length,
      fraudAlerts: this.fraudAlerts().length,
      fraudEvaluations: this.fraudEvaluations().length,
    };
  }
}
