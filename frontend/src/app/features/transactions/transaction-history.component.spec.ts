import { describe, it, expect } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { TransactionHistoryComponent } from './transaction-history.component';
import { Transaction } from '../../shared/models/transaction.model';

const TXN: Transaction = {
  transactionReference: 'TXN-20260918-000001',
  accountId: 3,
  accountNumber: '100000000001',
  transactionType: 'WITHDRAWAL',
  amount: 400,
  currency: 'INR',
  balanceBefore: 1000,
  balanceAfter: 600,
  status: 'COMPLETED',
  description: 'ATM withdrawal',
  transferReference: null,
  counterpartyAccountNumber: null,
  createdAt: '2026-09-18T10:00:00Z',
  completedAt: '2026-09-18T10:00:01Z',
};

describe('TransactionHistoryComponent', () => {
  it('shows a loading state', () => {
    const fixture = TestBed.createComponent(TransactionHistoryComponent);
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading transactions');
  });

  it('shows an empty state when there are no transactions', () => {
    const fixture = TestBed.createComponent(TransactionHistoryComponent);
    fixture.componentRef.setInput('transactions', []);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No transactions yet');
  });

  it('renders reference, type, amount, balances, status and description', () => {
    const fixture = TestBed.createComponent(TransactionHistoryComponent);
    fixture.componentRef.setInput('transactions', [TXN]);
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('TXN-20260918-000001');
    expect(text).toContain('WITHDRAWAL');
    expect(text).toContain('400');
    expect(text).toContain('COMPLETED');
    expect(text).toContain('1000');
    expect(text).toContain('600');
    expect(text).toContain('ATM withdrawal');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('renders transfer legs with shared reference, direction and counterparty', () => {
    const fixture = TestBed.createComponent(TransactionHistoryComponent);
    fixture.componentRef.setInput('transactions', [
      {
        ...TXN,
        transactionReference: 'TRF-20260918-000001-D',
        transactionType: 'TRANSFER_DEBIT',
        transferReference: 'TRF-20260918-000001',
        counterpartyAccountNumber: '100000000002',
      },
    ]);
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('TRANSFER_DEBIT');
    expect(text).toContain('TRF-20260918-000001 to 100000000002');
  });
});
