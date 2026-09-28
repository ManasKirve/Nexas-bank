import { describe, it, expect } from 'vitest';
import { buildCustomerForm } from './customer-create.component';

describe('CustomerCreate form', () => {
  it('starts invalid and requires names + valid email', () => {
    const form = buildCustomerForm();
    expect(form.valid).toBe(false);

    form.patchValue({ firstName: 'Ann', lastName: 'Smith', email: 'not-an-email' });
    expect(form.controls['email'].valid).toBe(false);

    form.patchValue({ email: 'ann@example.com' });
    expect(form.valid).toBe(true);
  });

  it('rejects short/invalid phone numbers but allows empty', () => {
    const form = buildCustomerForm();
    form.patchValue({ firstName: 'Ann', lastName: 'Smith', email: 'ann@example.com' });
    expect(form.valid).toBe(true);

    form.patchValue({ phone: 'abc' });
    expect(form.controls['phone'].valid).toBe(false);

    form.patchValue({ phone: '+91 98765 43210' });
    expect(form.valid).toBe(true);
  });
});
