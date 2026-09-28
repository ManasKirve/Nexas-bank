import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { CustomerService } from '../../core/services/customer.service';

export function buildCustomerForm(): FormGroup {
  return new FormGroup({
    firstName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    lastName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(255)],
    }),
    phone: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(32), Validators.pattern(/^[+()\-\s\d]{7,32}$/)],
    }),
  });
}

@Component({
  selector: 'app-customer-create',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="page narrow">
      <h1>New customer</h1>
      <p class="muted">Creates a customer via POST /api/v1/customers.</p>
      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="card form" novalidate>
        <label>First name
          <input formControlName="firstName" autocomplete="given-name" />
          @if (form.controls['firstName'].touched && form.controls['firstName'].invalid) {
            <span class="field-error">First name is required (max 100 characters).</span>
          }
        </label>
        <label>Last name
          <input formControlName="lastName" autocomplete="family-name" />
          @if (form.controls['lastName'].touched && form.controls['lastName'].invalid) {
            <span class="field-error">Last name is required (max 100 characters).</span>
          }
        </label>
        <label>Email
          <input formControlName="email" type="email" autocomplete="email" />
          @if (form.controls['email'].touched && form.controls['email'].invalid) {
            <span class="field-error">Enter a valid email address.</span>
          }
        </label>
        <label>Phone (optional)
          <input formControlName="phone" autocomplete="tel" placeholder="+91 98765 43210" />
          @if (form.controls['phone'].touched && form.controls['phone'].invalid) {
            <span class="field-error">Phone must be 7–32 digits, may include +, spaces, hyphens.</span>
          }
        </label>
        @if (error()) {
          <p class="form-error" role="alert">{{ error() }}</p>
        }
        @if (success()) {
          <p class="form-success" role="status">{{ success() }}</p>
        }
        <div class="row">
          <button type="submit" [disabled]="form.invalid || saving()">
            {{ saving() ? 'Saving…' : 'Create customer' }}
          </button>
          <a routerLink="/customers" class="cancel">Cancel</a>
        </div>
      </form>
    </section>
  `,
  styles: [
    `
      .narrow { max-width: 560px; margin: 0 auto; }
      .form { display: grid; gap: 0.85rem; }
      label { display: grid; gap: 0.35rem; font-weight: 600; font-size: 0.9rem; }
      input { padding: 0.6rem; border: 1px solid #dadce0; border-radius: 8px; font-size: 1rem; font-weight: 400; }
      button { padding: 0.65rem; border-radius: 8px; border: 0; background: #174ea6; color: #fff; font-weight: 600; cursor: pointer; }
      button:disabled { opacity: 0.55; cursor: not-allowed; }
      .row { display: flex; gap: 0.75rem; align-items: center; }
      .cancel { color: #174ea6; }
      .field-error { color: #a50e0e; font-size: 0.8rem; font-weight: 400; }
      .form-error { color: #a50e0e; background: #fce8e6; border: 1px solid #f5b5b0; padding: 0.6rem; border-radius: 8px; margin: 0; }
      .form-success { color: #137333; background: #e6f4ea; border: 1px solid #b7dfc2; padding: 0.6rem; border-radius: 8px; margin: 0; }
    `,
  ],
})
export class CustomerCreateComponent {
  private readonly customersApi = inject(CustomerService);
  private readonly router = inject(Router);
  readonly form = buildCustomerForm();
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.customersApi
      .create({ ...value, phone: value.phone || undefined })
      .subscribe({
        next: (created) => {
          this.success.set(`Customer ${created.customerNumber} created.`);
          this.saving.set(false);
          setTimeout(() => this.router.navigate(['/customers', created.id]), 600);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(readError(err));
          this.saving.set(false);
        },
      });
  }
}

export function readError(err: HttpErrorResponse): string {
  const body = err.error as { message?: string; errors?: Record<string, string> } | undefined;
  if (body?.errors) {
    return Object.entries(body.errors)
      .map(([field, message]) => `${field}: ${message}`)
      .join(' ');
  }
  if (typeof body?.message === 'string' && body.message.length > 0) {
    return body.message;
  }
  if (err.status === 0) {
    return 'LocalStorage is unavailable, so changes cannot be saved. Check your browser privacy settings.';
  }
  return `Request failed (HTTP ${err.status}).`;
}
