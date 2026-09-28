import { Injectable, inject } from '@angular/core';
import { Observable, defer, of, throwError } from 'rxjs';
import { Beneficiary } from '../../shared/models/beneficiary.model';
import { DemoRepositoryService } from './demo-repository.service';
import { ERRORS } from './demo-api';

/**
 * Local implementation of /api/v1/beneficiaries.
 *
 * Listing is scoped to the acting demo customer — the backend derived that from
 * the JWT, here it comes from the Admin selection. Creation validates that the
 * target account exists, so the new-beneficiary fraud rule always has a real
 * `createdAt` to work with.
 */
@Injectable({ providedIn: 'root' })
export class BeneficiaryService {
  private readonly repo = inject(DemoRepositoryService);

  list(): Observable<Beneficiary[]> {
    return defer(() => {
      const customerId = this.repo.activeCustomerId();
      return of(
        this.repo
          .beneficiaries()
          .filter((b) => b.customerId === customerId)
          .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)),
      );
    });
  }

  get(id: number): Observable<Beneficiary> {
    return defer(() => {
      const found = this.repo.beneficiaries().find((b) => b.id === id);
      return found ? of(found) : throwError(() => ERRORS.notFound('Beneficiary', id));
    });
  }

  create(accountId: number, nickname?: string | null): Observable<Beneficiary> {
    return defer(() => {
      const customerId = this.repo.activeCustomerId();
      const owner = this.repo.accounts().find((a) => a.id === accountId);
      if (!owner) {
        return throwError(() => ERRORS.notFound('Account', accountId));
      }
      if (owner.customerId !== customerId) {
        return throwError(
          () => ERRORS.badState(`Account ${owner.accountNumber} does not belong to the selected customer`),
        );
      }
      const label = nickname?.trim() ? nickname.trim() : `Payee ${owner.accountNumber}`;
      const now = new Date().toISOString();
      const rows = this.repo.beneficiaries();
      const created: Beneficiary = {
        id: rows.reduce((max, b) => Math.max(max, b.id), 0) + 1,
        customerId,
        beneficiaryAccountId: owner.id,
        beneficiaryAccountNumber: owner.accountNumber,
        nickname: label,
        status: 'ACTIVE',
        createdAt: now,
        updatedAt: now,
      };
      this.repo.saveBeneficiaries([...rows, created]);
      this.repo.appendActivity(
        'BENEFICIARY',
        `Beneficiary ${label} added for ${owner.accountNumber}`,
        null,
        now,
      );
      return of(created);
    });
  }

  /** Soft disable (ACTIVE → DISABLED); past transfers keep their history. */
  disable(id: number): Observable<Beneficiary> {
    return defer(() => {
      const rows = this.repo.beneficiaries();
      const existing = rows.find((b) => b.id === id);
      if (!existing) {
        return throwError(() => ERRORS.notFound('Beneficiary', id));
      }
      if (existing.status === 'DISABLED') {
        return throwError(() => ERRORS.badState(`Beneficiary ${existing.nickname} is already disabled`));
      }
      const updated: Beneficiary = { ...existing, status: 'DISABLED', updatedAt: new Date().toISOString() };
      this.repo.saveBeneficiaries(rows.map((b) => (b.id === id ? updated : b)));
      this.repo.appendActivity('BENEFICIARY', `Beneficiary ${updated.nickname} disabled`);
      return of(updated);
    });
  }
}
