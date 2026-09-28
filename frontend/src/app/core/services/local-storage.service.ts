import { Injectable, computed, signal } from '@angular/core';

/**
 * The single gateway to the browser's LocalStorage.
 *
 * No component and no feature service ever calls `localStorage.getItem()` /
 * `setItem()` directly — they all go through this service, so storage keys
 * stay in one place and the rest of the app deals in typed objects.
 *
 * Demo data survives browser refreshes and browser restarts because
 * LocalStorage is origin-scoped and persistent. It is seeded exactly once: on
 * the very first launch the dataset below is written, and on every later
 * launch existing data is left untouched.
 *
 * The service is deliberately tolerant — if LocalStorage is unavailable
 * (private mode, disabled cookies, quota exhausted) every operation degrades to
 * a no-op instead of throwing, so the application keeps running.
 */

export const STORAGE_KEYS = {
  /** Sentinel proving the demo dataset was seeded at least once. */
  marker: 'nexabank_demo_initialized',
  /** Schema version, so a future dataset change can re-seed deliberately. */
  version: 'nexabank_demo_schema_version',
  customers: 'nexabank_customers',
  accounts: 'nexabank_accounts',
  transactions: 'nexabank_transactions',
  beneficiaries: 'nexabank_beneficiaries',
  fraudAlerts: 'nexabank_fraud_alerts',
  fraudEvaluations: 'nexabank_fraud_evaluations',
  auditLog: 'nexabank_audit_log',
  /** Demo customer the "my accounts / my money" views act on behalf of. */
  activeCustomerId: 'nexabank_active_customer_id',
} as const;

export type StorageKey = (typeof STORAGE_KEYS)[keyof typeof STORAGE_KEYS];

/** Bumped whenever the seed data shape changes in a way that needs a re-seed. */
export const DEMO_SCHEMA_VERSION = 1;

/** Every key this app owns; used by the reset action. */
export const ALL_STORAGE_KEYS: string[] = Object.values(STORAGE_KEYS);

function resolveStorage(): Storage | null {
  try {
    if (typeof localStorage === 'undefined') {
      return null;
    }
    const probe = '__nexabank_probe__';
    localStorage.setItem(probe, '1');
    localStorage.removeItem(probe);
    return localStorage;
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class LocalStorageService {
  private readonly storage = resolveStorage();

  /**
   * Bumped on every write. Screens can watch it with an `effect()` to re-read
   * after a mutation performed elsewhere, so the UI stays live without polling.
   */
  private readonly _revision = signal(0);
  readonly revision = this._revision.asReadonly();
  readonly revisionNumber = computed(() => this._revision());

  /** True when LocalStorage is usable at all. */
  readonly available = this.storage !== null;

  /** Reads and parses a key. Returns `fallback` for missing or corrupt data. */
  read<T>(key: string, fallback: T): T {
    if (!this.storage) {
      return fallback;
    }
    try {
      const raw = this.storage.getItem(key);
      if (raw === null) {
        return fallback;
      }
      return JSON.parse(raw) as T;
    } catch {
      return fallback;
    }
  }

  /** Reads a collection key, always returning an array. */
  readList<T>(key: string): T[] {
    const value = this.read<T[]>(key, []);
    return Array.isArray(value) ? value : [];
  }

  /** Serialises and stores a value, then notifies watchers. */
  write<T>(key: string, value: T): void {
    if (!this.storage) {
      return;
    }
    try {
      this.storage.setItem(key, JSON.stringify(value));
      this._revision.update((n) => n + 1);
    } catch {
      // Quota exceeded or storage disabled — the demo keeps running in memory.
    }
  }

  has(key: string): boolean {
    if (!this.storage) {
      return false;
    }
    try {
      return this.storage.getItem(key) !== null;
    } catch {
      return false;
    }
  }

  remove(key: string): void {
    if (!this.storage) {
      return;
    }
    try {
      this.storage.removeItem(key);
      this._revision.update((n) => n + 1);
    } catch {
      // Nothing actionable here.
    }
  }

  /** Reads a plain numeric/string scalar, used for the active customer id. */
  readScalar(key: string): string | null {
    if (!this.storage) {
      return null;
    }
    try {
      return this.storage.getItem(key);
    } catch {
      return null;
    }
  }

  writeScalar(key: string, value: string): void {
    if (!this.storage) {
      return;
    }
    try {
      this.storage.setItem(key, value);
      this._revision.update((n) => n + 1);
    } catch {
      // Ignored on purpose — see write().
    }
  }

  // ---------- demo lifecycle ----------

  /**
   * True when a previous launch already seeded the dataset. Used to decide
   * between "seed now" and "keep what the user has".
   */
  isSeeded(): boolean {
    if (!this.has(STORAGE_KEYS.marker)) {
      return false;
    }
    return this.readScalar(STORAGE_KEYS.version) === String(DEMO_SCHEMA_VERSION);
  }

  /** Removes every NexaBank key. Callers re-seed afterwards. */
  clearAll(): void {
    for (const key of ALL_STORAGE_KEYS) {
      this.remove(key);
    }
  }

  /** Approximate size of the stored demo data, shown in the admin screen. */
  approximateSizeKb(): number {
    if (!this.storage) {
      return 0;
    }
    let bytes = 0;
    for (const key of ALL_STORAGE_KEYS) {
      try {
        const value = this.storage.getItem(key);
        if (value) {
          bytes += key.length + value.length;
        }
      } catch {
        // Ignore unreadable keys.
      }
    }
    return Math.round((bytes / 1024) * 10) / 10;
  }
}
