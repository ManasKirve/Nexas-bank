import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateCustomerRequest,
  Customer,
  UpdateCustomerRequest,
} from '../../shared/models/customer.model';

/**
 * Customer directory API client (`/api/v1/customers`).
 *
 * Management operations (list/create/update/deactivate) require BANK_EMPLOYEE
 * or ADMIN; `/customers/me` returns the authenticated customer's own profile.
 * The backend remains the authorization boundary.
 */
@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  getAll(): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${this.base}/api/v1/customers`);
  }

  getById(id: number): Observable<Customer> {
    return this.http.get<Customer>(`${this.base}/api/v1/customers/${id}`);
  }

  /** The authenticated customer's own profile (JWT-derived identity). */
  getMyProfile(): Observable<Customer> {
    return this.http.get<Customer>(`${this.base}/api/v1/customers/me`);
  }

  create(request: CreateCustomerRequest): Observable<Customer> {
    return this.http.post<Customer>(`${this.base}/api/v1/customers`, request);
  }

  update(id: number, request: UpdateCustomerRequest): Observable<Customer> {
    return this.http.put<Customer>(`${this.base}/api/v1/customers/${id}`, request);
  }

  /** Soft delete: the backend flips the customer to INACTIVE. */
  deactivate(id: number): Observable<Customer> {
    return this.http.delete<Customer>(`${this.base}/api/v1/customers/${id}`);
  }
}
