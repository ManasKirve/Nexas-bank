import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard.component';
import { Account } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';

const ACCOUNTS: Account[] = [
  { id: 1, accountNumber: '100000000001', customerId: 1, customerNumber: 'CUST-100001', accountType: 'SAVINGS', balance: 1000, currency: 'INR', status: 'ACTIVE', createdAt: '', updatedAt: '' },
  { id: 2, accountNumber: '100000000002', customerId: 1, customerNumber: 'CUST-100001', accountType: 'CHECKING', balance: 250, currency: 'INR', status: 'ACTIVE', createdAt: '', updatedAt: '' },
];

function txn(ref: string, type: Transaction['transactionType'], amount: number): Transaction {
  return {
    transactionReference: ref,
    accountId: 1,
    accountNumber: '100000000001',
    transactionType: type,
    amount,
    currency: 'INR',
    balanceBefore: 0,
    balanceAfter: amount,
    status: 'COMPLETED',
    description: null,
    transferReference: null,
    counterpartyAccountNumber: null,
    createdAt: '2026-09-18T10:00:00Z',
    completedAt: '2026-09-18T10:00:01Z',
  };
}

describe('DashboardComponent finances', () => {
  let component: DashboardComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    component = TestBed.runInInjectionContext(() => new DashboardComponent());
  });

  it('totals combined balances across own accounts', () => {
    component.myAccounts.set(ACCOUNTS);
    expect(component.totalBalance()).toBe(1250);
  });

  it('reports a zero balance before any accounts load', () => {
    // The dashboard starts with null (not []) while the query is in flight.
    expect(component.totalBalance()).toBe(0);
  });

  it('caps recent transfers at five rows like recent transactions', () => {
    component.myTransactions.set(
      Array.from({ length: 7 }, (_, i) => ({
        ...txn(`TRF-${i}-D`, 'TRANSFER_DEBIT', 10),
        transferReference: `TRF-${i}`,
      })),
    );
    expect(component.recentTransfers().length).toBe(5);
  });

  it('sums completed deposits and withdrawals separately', () => {
    component.myTransactions.set([
      txn('TXN-1', 'DEPOSIT', 1000),
      txn('TXN-2', 'WITHDRAWAL', 400),
      { ...txn('TXN-3', 'DEPOSIT', 999), status: 'FAILED' },
    ]);
    expect(component.totalDeposits()).toBe(1000);
    expect(component.totalWithdrawals()).toBe(400);
  });

  it('shows only the five most recent transactions', () => {
    component.myTransactions.set(
      Array.from({ length: 7 }, (_, i) => txn(`TXN-${i}`, 'DEPOSIT', 10)),
    );
    expect(component.recentTransactions().length).toBe(5);
  });

  it('isolates transfers and totals sent vs received', () => {
    component.myTransactions.set([
      txn('TXN-1', 'DEPOSIT', 1000),
      { ...txn('TRF-1-D', 'TRANSFER_DEBIT', 250), transferReference: 'TRF-1', counterpartyAccountNumber: '100000000002' },
      { ...txn('TRF-1-C', 'TRANSFER_CREDIT', 400), transferReference: 'TRF-2', counterpartyAccountNumber: '100000000003' },
    ]);
    expect(component.recentTransfers().length).toBe(2);
    expect(component.totalSent()).toBe(250);
    expect(component.totalReceived()).toBe(400);
    // Deposit/withdrawal totals ignore transfer legs.
    expect(component.totalDeposits()).toBe(1000);
    expect(component.totalWithdrawals()).toBe(0);
  });
});
