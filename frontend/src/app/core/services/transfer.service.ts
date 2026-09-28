import { Injectable, inject } from '@angular/core';
import { Observable, defer, of } from 'rxjs';
import { TransferResult } from '../../shared/models/beneficiary.model';
import { Transaction } from '../../shared/models/transaction.model';
import { Account } from '../../shared/models/account.model';
import { DemoRepositoryService } from './demo-repository.service';
import { LedgerService } from './ledger.service';
import { ERRORS, isValidAmount, round2 } from './demo-api';

/**
 * Local implementation of POST /api/v1/transfers.
 *
 * A transfer is double-entry: one `TRANSFER_DEBIT` leg and one
 * `TRANSFER_CREDIT` leg sharing a single `TRF-…` reference, so both accounts'
 * histories reconcile. The fraud engine evaluates the outgoing leg before any
 * balance moves.
 */
@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly repo = inject(DemoRepositoryService);
  private readonly ledger = inject(LedgerService);

  transfer(
    sourceAccountId: number,
    beneficiaryId: number,
    amount: number,
    description?: string | null,
  ): Observable<TransferResult> {
    return defer(() => of(this.execute(sourceAccountId, beneficiaryId, amount, description)));
  }

  private execute(
    sourceAccountId: number,
    beneficiaryId: number,
    amount: number,
    description?: string | null,
  ): TransferResult {
    if (!isValidAmount(amount)) {
      throw ERRORS.invalid(`Amount must be a positive value with at most 2 decimals, got ${amount}`);
    }
    const beneficiary = this.repo.beneficiaries().find((b) => b.id === beneficiaryId);
    if (!beneficiary) {
      throw ERRORS.notFound('Beneficiary', beneficiaryId);
    }
    if (beneficiary.status !== 'ACTIVE') {
      throw ERRORS.badState(`Beneficiary ${beneficiary.nickname} is DISABLED`);
    }
    const source = this.ledger.requireActiveAccount(sourceAccountId);
    if (beneficiary.beneficiaryAccountId === sourceAccountId) {
      throw ERRORS.invalid('Cannot transfer to the same account');
    }
    const destination = this.repo
      .accounts()
      .find((a) => a.id === beneficiary.beneficiaryAccountId);
    if (!destination) {
      throw ERRORS.notFound('Beneficiary account', beneficiary.beneficiaryAccountId);
    }
    this.ledger.requireFunds(source, amount);

    const now = new Date();
    return this.ledger.attempt({
      account: source,
      amount,
      type: 'TRANSFER_DEBIT',
      beneficiaryId,
      now,
      settle: (reference) => this.settle(source, destination, amount, description, reference),
    });
  }

  /** Writes both legs and returns the projection the backend returned. */
  private settle(
    source: Account,
    destination: Account,
    amount: number,
    description: string | null | undefined,
    reference: string,
  ): TransferResult {
    const accounts = this.repo.accounts();
    const currentSource = accounts.find((a) => a.id === source.id);
    const currentDestination = accounts.find((a) => a.id === destination.id);
    if (!currentSource || !currentDestination) {
      throw ERRORS.notFound('Account', !currentSource ? source.id : destination.id);
    }

    const sourceBefore = currentSource.balance;
    const sourceAfter = round2(sourceBefore - amount);
    const destinationBefore = currentDestination.balance;
    const destinationAfter = round2(destinationBefore + amount);
    const now = new Date().toISOString();
    const note = description?.trim() ? description.trim() : null;

    this.repo.saveAccounts(
      accounts.map((a) => {
        if (a.id === currentSource.id) {
          return { ...a, balance: sourceAfter, updatedAt: now };
        }
        if (a.id === currentDestination.id) {
          return { ...a, balance: destinationAfter, updatedAt: now };
        }
        return a;
      }),
    );

    const debit: Transaction = {
      transactionReference: `${reference}-D`,
      accountId: currentSource.id,
      accountNumber: currentSource.accountNumber,
      transactionType: 'TRANSFER_DEBIT',
      amount,
      currency: currentSource.currency,
      balanceBefore: sourceBefore,
      balanceAfter: sourceAfter,
      status: 'COMPLETED',
      description: note,
      transferReference: reference,
      counterpartyAccountNumber: currentDestination.accountNumber,
      createdAt: now,
      completedAt: now,
    };
    const credit: Transaction = {
      transactionReference: `${reference}-C`,
      accountId: currentDestination.id,
      accountNumber: currentDestination.accountNumber,
      transactionType: 'TRANSFER_CREDIT',
      amount,
      currency: currentDestination.currency,
      balanceBefore: destinationBefore,
      balanceAfter: destinationAfter,
      status: 'COMPLETED',
      description: note,
      transferReference: reference,
      counterpartyAccountNumber: currentSource.accountNumber,
      createdAt: now,
      completedAt: now,
    };
    this.repo.saveTransactions([...this.repo.transactions(), debit, credit]);

    return {
      transferReference: reference,
      sourceAccountId: currentSource.id,
      sourceAccountNumber: currentSource.accountNumber,
      destinationAccountId: currentDestination.id,
      destinationAccountNumber: currentDestination.accountNumber,
      amount,
      currency: currentSource.currency,
      sourceBalanceBefore: sourceBefore,
      sourceBalanceAfter: sourceAfter,
      destinationBalanceBefore: destinationBefore,
      destinationBalanceAfter: destinationAfter,
      status: 'COMPLETED',
      createdAt: now,
    };
  }
}
