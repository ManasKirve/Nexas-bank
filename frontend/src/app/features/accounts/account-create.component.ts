import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AccountService } from '../../core/services/account.service';
import { CustomerService } from '../../core/services/customer.service';
import { AccountType } from '../../shared/models/account.model';
import { Customer } from '../../shared/models/customer.model';
import { readError } from '../customers/customer-create.component';

export function buildAccountForm(preselectedCustomerId: number | null): FormGroup {
  return new FormGroup({
    customerId: new FormControl<number | null>(preselectedCustomerId, {
      validators: [Validators.required],
    }),
    accountType: new FormControl<AccountType | null>(null, {
      validators: [Validators.required],
    }),
    currency: new FormControl('INR', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^INR$/)],
    }),
  });
}

@Component({
  selector: 'app-account-create',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="page narrow">
      <h1>Open account</h1>
      <p class="muted">New accounts start with a zero balance — deposits arrive in a later phase.</p>
      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="card form" novalidate>
        <label>Customer
          <select formControlName="customerId">
            <option [ngValue]="null" disabled>Select a customer</option>
            @for (c of customers(); track c.id) {
              <option [ngValue]="c.id">{{ c.customerNumber }} — {{ c.firstName }} {{ c.lastName }}</option>
            }
          </select>
          @if (form.controls['customerId'].touched && form.controls['customerId'].invalid) {
            <span class="field-error">Choose the owning customer.</span>
          }
        </label>
        <label>Account type
          <select formControlName="accountType">
            <option [ngValue]="null" disabled>Select a type</option>
            <option value="SAVINGS">Savings</option>
            <option value="CHECKING">Checking</option>
          </select>
          @if (form.controls['accountType'].touched && form.controls['accountType'].invalid) {
            <span class="field-error">Choose savings or checking.</span>
          }
        </label>
        <label>Currency
          <input formControlName="currency" readonly />
        </label>
        @if (error()) {
          <p class="form-error" role="alert">{{ error() }}</p>
        }
        @if (success()) {
          <p class="form-success" role="status">{{ success() }}</p>
        }
        <div class="row">
          <button type="submit" [disabled]="form.invalid || saving()">
            {{ saving() ? 'Opening…' : 'Open account' }}
          </button>
          <a routerLink="/accounts" class="cancel">Cancel</a>
        </div>
      </form>
    </section>
  `,
  styles: [
    `
      .narrow { max-width: 560px; margin: 0 auto; }
      .form { display: grid; gap: 0.85rem; }
      label { display: grid; gap: 0.35rem; font-weight: 600; font-size: 0.9rem; }
      input, select { padding: 0.6rem; border: 1px solid #dadce0; border-radius: 8px; font-size: 1rem; font-weight: 400; }
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
export class AccountCreateComponent implements OnInit {
  private readonly accountsApi = inject(AccountService);
  private readonly customersApi = inject(CustomerService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly customers = signal<Customer[]>([]);
  readonly form = buildAccountForm(this.preselectedCustomerId());
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  private preselectedCustomerId(): number | null {
    const raw = this.route.snapshot.queryParamMap.get('customerId');
    const parsed = raw === null ? NaN : Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }

  ngOnInit(): void {
    this.customersApi.getAll().subscribe({
      next: (rows) => this.customers.set(rows),
      error: (err: HttpErrorResponse) => this.error.set(readError(err)),
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.accountsApi
      .create({
        customerId: value.customerId as number,
        accountType: value.accountType as AccountType,
        currency: value.currency,
      })
      .subscribe({
        next: (created) => {
          this.success.set(`Account ${created.accountNumber} opened with zero balance.`);
          this.saving.set(false);
          setTimeout(() => this.router.navigate(['/accounts', created.id]), 600);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(readError(err));
          this.saving.set(false);
        },
      });
  }
}
