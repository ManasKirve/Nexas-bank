import { Injectable, inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import {
  CreateCustomerRequest,
  Customer,
  UpdateCustomerRequest,
} from '../../shared/models/customer.model';
import { DemoRepositoryService } from './demo-repository.service';
import { ERRORS, demoValidationError } from './demo-api';

/**
 * Customer directory, served from the local demo store.
 *
 * The observable contract and the validation rules are unchanged from the
 * `/api/v1/customers` client — only the storage moved from PostgreSQL to the
 * browser. Failures still arrive as `HttpErrorResponse`s (404, 409, 400) so
 * the components render errors exactly as before.
 */
@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly repo = inject(DemoRepositoryService);

  getAll(): Observable<Customer[]> {
    return of(this.repo.customers());
  }

  getById(id: number): Observable<Customer> {
    const found = this.repo.customers().find((c) => c.id === id);
    return found ? of(found) : throwError(() => ERRORS.notFound('Customer', id));
  }

  /** The demo customer the "my profile" views act for. */
  getMyProfile(): Observable<Customer> {
    const id = this.repo.activeCustomerId();
    return this.getById(id);
  }

  create(request: CreateCustomerRequest): Observable<Customer> {
    const invalid = validate(request);
    if (invalid) {
      return throwError(() => demoValidationError(invalid));
    }
    const rows = this.repo.customers();
    if (rows.some((c) => c.email.toLowerCase() === request.email.trim().toLowerCase())) {
      return throwError(() => ERRORS.duplicate('Email ' + request.email + ' is already registered'));
    }
    const now = new Date().toISOString();
    const created: Customer = {
      id: this.nextId(rows),
      customerNumber: this.nextCustomerNumber(rows),
      firstName: request.firstName.trim(),
      lastName: request.lastName.trim(),
      email: request.email.trim(),
      phone: request.phone?.trim() ? request.phone.trim() : null,
      status: 'ACTIVE',
      createdAt: now,
      updatedAt: now,
    };
    this.repo.saveCustomers([...rows, created]);
    this.repo.appendActivity('CUSTOMER', `Customer ${created.customerNumber} created`, null, created.email);
    return of(created);
  }

  update(id: number, request: UpdateCustomerRequest): Observable<Customer> {
    const invalid = validate(request);
    if (invalid) {
      return throwError(() => demoValidationError(invalid));
    }
    const rows = this.repo.customers();
    const existing = rows.find((c) => c.id === id);
    if (!existing) {
      return throwError(() => ERRORS.notFound('Customer', id));
    }
    if (rows.some((c) => c.id !== id && c.email.toLowerCase() === request.email.trim().toLowerCase())) {
      return throwError(() => ERRORS.duplicate('Email ' + request.email + ' is already registered'));
    }
    const updated: Customer = {
      ...existing,
      firstName: request.firstName.trim(),
      lastName: request.lastName.trim(),
      email: request.email.trim(),
      phone: request.phone?.trim() ? request.phone.trim() : null,
      updatedAt: new Date().toISOString(),
    };
    this.repo.saveCustomers(rows.map((c) => (c.id === id ? updated : c)));
    return of(updated);
  }

  /** Soft delete, exactly as the backend did: flip to INACTIVE, keep the row. */
  deactivate(id: number): Observable<Customer> {
    const rows = this.repo.customers();
    const existing = rows.find((c) => c.id === id);
    if (!existing) {
      return throwError(() => ERRORS.notFound('Customer', id));
    }
    const updated: Customer = { ...existing, status: 'INACTIVE', updatedAt: new Date().toISOString() };
    this.repo.saveCustomers(rows.map((c) => (c.id === id ? updated : c)));
    this.repo.appendActivity(
      'CUSTOMER',
      `Customer ${updated.customerNumber} deactivated`,
      null,
      null,
    );
    return of(updated);
  }

  private nextId(rows: Customer[]): number {
    return rows.reduce((max, c) => Math.max(max, c.id), 0) + 1;
  }

  /** Mirrors the backend's `CUST-100001` sequence. */
  private nextCustomerNumber(rows: Customer[]): string {
    const highest = rows.reduce((max, c) => {
      const parsed = Number(c.customerNumber.replace('CUST-', ''));
      return Number.isFinite(parsed) ? Math.max(max, parsed) : max;
    }, 100_000);
    return `CUST-${highest + 1}`;
  }
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_PATTERN = /^[+()\-\s\d]{7,32}$/;

/** Field-level checks mirroring the bean validation on the backend DTOs. */
function validate(request: CreateCustomerRequest): Record<string, string> | null {
  const errors: Record<string, string> = {};
  if (!request.firstName?.trim()) {
    errors['firstName'] = 'First name is required';
  } else if (request.firstName.trim().length > 100) {
    errors['firstName'] = 'First name must be at most 100 characters';
  }
  if (!request.lastName?.trim()) {
    errors['lastName'] = 'Last name is required';
  } else if (request.lastName.trim().length > 100) {
    errors['lastName'] = 'Last name must be at most 100 characters';
  }
  if (!request.email?.trim()) {
    errors['email'] = 'Email is required';
  } else if (!EMAIL_PATTERN.test(request.email.trim()) || request.email.trim().length > 255) {
    errors['email'] = 'Email must be a valid address';
  }
  if (request.phone && request.phone.trim() && !PHONE_PATTERN.test(request.phone.trim())) {
    errors['phone'] = 'Phone must be 7-32 digits and may include +, spaces or hyphens';
  }
  return Object.keys(errors).length > 0 ? errors : null;
}
