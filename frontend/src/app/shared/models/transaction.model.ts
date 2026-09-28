/** Phase 4 transaction engine models (ledger projections — never JPA entities). */

export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER_DEBIT' | 'TRANSFER_CREDIT';
export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REVERSED';

export interface Transaction {
  transactionReference: string;
  accountId: number;
  accountNumber: string;
  transactionType: TransactionType;
  amount: number;
  currency: string;
  balanceBefore: number;
  balanceAfter: number;
  status: TransactionStatus;
  description: string | null;
  /** Shared TRF-… reference linking a transfer's debit+credit legs (null for deposits/withdrawals). */
  transferReference: string | null;
  /** Other side's account number for transfers (null otherwise; no private customer data). */
  counterpartyAccountNumber: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface DepositRequest {
  amount: number;
  description?: string | null;
  idempotencyKey: string;
}

export interface WithdrawalRequest {
  amount: number;
  description?: string | null;
  idempotencyKey: string;
}

export interface TransactionPage {
  content: Transaction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Client-generated idempotency key (server enforces uniqueness). */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}
