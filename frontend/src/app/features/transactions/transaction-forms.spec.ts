import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AccountDetailComponent } from '../accounts/account-detail.component';

describe('AccountDetailComponent money forms', () => {
  let component: AccountDetailComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    component = TestBed.runInInjectionContext(() => new AccountDetailComponent());
  });

  it('deposit form starts invalid', () => {
    expect(component.depositForm.invalid).toBe(true);
  });

  it('withdraw form starts invalid', () => {
    expect(component.withdrawForm.invalid).toBe(true);
  });

  it('accepts a positive amount with an optional description', () => {
    component.depositForm.setValue({ amount: '1000.00', description: 'Cash deposit' });
    expect(component.depositForm.valid).toBe(true);
    component.withdrawForm.setValue({ amount: '500', description: null });
    expect(component.withdrawForm.valid).toBe(true);
  });

  it('rejects zero, negative and non-numeric amounts', () => {
    for (const amount of ['0', '0.00', '-50', 'abc', '']) {
      component.depositForm.setValue({ amount, description: null });
      expect(component.depositForm.controls.amount.invalid).toBe(true);
      component.withdrawForm.setValue({ amount, description: null });
      expect(component.withdrawForm.controls.amount.invalid).toBe(true);
    }
  });

  it('rejects more than two decimal places', () => {
    component.depositForm.setValue({ amount: '10.999', description: null });
    expect(component.depositForm.controls.amount.invalid).toBe(true);
  });

  it('rejects overlong descriptions', () => {
    component.depositForm.setValue({ amount: '10', description: 'x'.repeat(501) });
    expect(component.depositForm.controls.description.invalid).toBe(true);
  });

  it('accepts exactly 500 characters of description', () => {
    component.depositForm.setValue({ amount: '10', description: 'x'.repeat(500) });
    expect(component.depositForm.valid).toBe(true);
  });

  it('rejects a sub-cent amount', () => {
    component.depositForm.setValue({ amount: '0.001', description: null });
    expect(component.depositForm.controls.amount.invalid).toBe(true);
  });

  it('offers only the legal next account statuses', () => {
    // No account loaded yet, so there is nothing to transition.
    expect(component.nextActions()).toEqual([]);
  });
});
