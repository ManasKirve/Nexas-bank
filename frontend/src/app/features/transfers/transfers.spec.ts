import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TransfersComponent } from './transfers.component';

describe('TransfersComponent transfer form', () => {
  let component: TransfersComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    component = TestBed.runInInjectionContext(() => new TransfersComponent());
  });

  it('starts invalid', () => {
    expect(component.transferForm.invalid).toBe(true);
  });

  it('accepts source, beneficiary and a positive amount', () => {
    component.transferForm.setValue({
      sourceAccountId: 3,
      beneficiaryId: 1,
      amount: '250.00',
      description: 'Rent',
    });
    expect(component.transferForm.valid).toBe(true);
  });

  it('requires source and beneficiary selection', () => {
    component.transferForm.setValue({
      sourceAccountId: null,
      beneficiaryId: null,
      amount: '250.00',
      description: null,
    });
    expect(component.transferForm.controls.sourceAccountId.invalid).toBe(true);
    expect(component.transferForm.controls.beneficiaryId.invalid).toBe(true);
  });

  it('rejects zero, negative and over-precise amounts', () => {
    for (const amount of ['0', '-10', '10.999', 'abc']) {
      component.transferForm.setValue({
        sourceAccountId: 3,
        beneficiaryId: 1,
        amount,
        description: null,
      });
      expect(component.transferForm.controls.amount.invalid).toBe(true);
    }
  });

  it('exposes only active beneficiaries for selection', () => {
    component.beneficiaries.set([
      {
        id: 1, customerId: 7, beneficiaryAccountId: 9, beneficiaryAccountNumber: '100000000002',
        nickname: 'Active', status: 'ACTIVE', createdAt: '', updatedAt: '',
      },
      {
        id: 2, customerId: 7, beneficiaryAccountId: 10, beneficiaryAccountNumber: '100000000003',
        nickname: 'Off', status: 'DISABLED', createdAt: '', updatedAt: '',
      },
    ]);
    expect(component.activeBeneficiaries().map((b) => b.id)).toEqual([1]);
  });

  it('starts empty and not loading before the first load', () => {
    expect(component.loading()).toBe(true);
    expect(component.accounts()).toEqual([]);
    expect(component.activeBeneficiaries()).toEqual([]);
  });

  it('accepts the minimum valid amount of 0.01', () => {
    component.transferForm.setValue({
      sourceAccountId: 3,
      beneficiaryId: 1,
      amount: '0.01',
      description: null,
    });
    expect(component.transferForm.valid).toBe(true);
  });
});

describe('TransfersComponent beneficiary form', () => {
  let component: TransfersComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    component = TestBed.runInInjectionContext(() => new TransfersComponent());
  });

  it('starts invalid', () => {
    expect(component.beneficiaryForm.invalid).toBe(true);
  });

  it('accepts an account id with optional nickname', () => {
    component.beneficiaryForm.setValue({ accountId: 12, nickname: 'My Savings' });
    expect(component.beneficiaryForm.valid).toBe(true);
    component.beneficiaryForm.setValue({ accountId: 12, nickname: null });
    expect(component.beneficiaryForm.valid).toBe(true);
  });

  it('rejects missing or non-positive account ids', () => {
    for (const accountId of [null, 0, -5]) {
      component.beneficiaryForm.setValue({ accountId, nickname: null });
      expect(component.beneficiaryForm.controls.accountId.invalid).toBe(true);
    }
  });
});