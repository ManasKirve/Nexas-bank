/**
 * Local demo activity trail.
 *
 * Replaces the backend's audit log table (`TransactionAuditService` +
 * the `audit` schema) in the browser-only demo. Every money movement, status
 * change, beneficiary change and fraud action is appended here so the admin
 * screen can show what the demo did.
 */

export type DemoActivityType =
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'TRANSFER'
  | 'ACCOUNT'
  | 'CUSTOMER'
  | 'BENEFICIARY'
  | 'FRAUD_ALERT'
  | 'FRAUD_EVALUATION'
  | 'SYSTEM';

export interface DemoActivityEntry {
  id: number;
  at: string;
  type: DemoActivityType;
  summary: string;
  reference: string | null;
  details: string | null;
}
