/** Phase 5 beneficiary + transfer models. */

export type BeneficiaryStatus = 'ACTIVE' | 'DISABLED';

export interface Beneficiary {
  id: number;
  customerId: number;
  beneficiaryAccountId: number;
  beneficiaryAccountNumber: string;
  nickname: string;
  status: BeneficiaryStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBeneficiaryRequest {
  accountId: number;
  nickname?: string | null;
}

export interface TransferRequest {
  sourceAccountId: number;
  beneficiaryId: number;
  amount: number;
  description?: string | null;
  idempotencyKey: string;
}

export interface TransferResult {
  transferReference: string;
  sourceAccountId: number;
  sourceAccountNumber: string;
  destinationAccountId: number;
  destinationAccountNumber: string;
  amount: number;
  currency: string;
  sourceBalanceBefore: number;
  sourceBalanceAfter: number;
  destinationBalanceBefore: number;
  destinationBalanceAfter: number;
  status: 'COMPLETED' | 'PENDING' | 'FAILED' | 'REVERSED';
  createdAt: string;
}
