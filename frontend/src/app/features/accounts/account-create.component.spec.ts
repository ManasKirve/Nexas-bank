import { describe, it, expect } from 'vitest';
import { buildAccountForm } from './account-create.component';

describe('AccountCreate form', () => {
  it('requires a customer and an account type, defaults currency to INR', () => {
    const form = buildAccountForm(null);
    expect(form.valid).toBe(false);

    form.patchValue({ customerId: 7, accountType: 'SAVINGS' });
    expect(form.valid).toBe(true);
    expect(form.getRawValue().currency).toBe('INR');
  });

  it('rejects non-INR currencies in this phase', () => {
    const form = buildAccountForm(7);
    form.patchValue({ accountType: 'CHECKING', currency: 'USD' });
    expect(form.controls['currency'].valid).toBe(false);
  });

  it('honours a preselected customer id', () => {
    const form = buildAccountForm(42);
    expect(form.controls['customerId'].value).toBe(42);
  });
});
