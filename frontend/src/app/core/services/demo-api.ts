import { HttpErrorResponse } from '@angular/common/http';

/**
 * Shared plumbing for the LocalStorage demo data layer.
 *
 * The Angular services in this folder keep the exact same public contract they
 * had when they talked to the Spring Boot backend: they return RxJS
 * `Observable`s and they fail with `HttpErrorResponse`s carrying the original
 * business status codes (404, 400, 409, 422). That means the feature
 * components and their error handling stay untouched while the data itself
 * now lives in the browser instead of PostgreSQL.
 *
 * Nothing in here talks to a network — these are pure helpers.
 */

/** Money is stored as a plain number; every mutation is rounded to 2 decimals. */
export function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

/** A positive amount with at most 2 decimals is the only shape we accept. */
export function isValidAmount(amount: number): boolean {
  return Number.isFinite(amount) && amount > 0 && round2(amount) === amount;
}

function pad(value: number, width: number): string {
  return String(value).padStart(width, '0');
}

/**
 * Sortable business reference, e.g. `TXN-20260918-000142`.
 *
 * Mirrors the backend generator (date prefix + random suffix) so demo data is
 * indistinguishable in shape from real data. `taken` keeps it unique inside the
 * local store.
 */
export function nextReference(prefix: 'TXN' | 'TRF', at: Date, taken: string[]): string {
  const date = `${at.getUTCFullYear()}${pad(at.getUTCMonth() + 1, 2)}${pad(at.getUTCDate(), 2)}`;
  for (let attempt = 0; attempt < 5; attempt++) {
    const candidate = `${prefix}-${date}-${pad(Math.floor(Math.random() * 1_000_000), 6)}`;
    if (!taken.includes(candidate)) {
      return candidate;
    }
  }
  return `${prefix}-${date}-${at.getTime()}`;
}

export function isAfter(iso: string, cutoff: number): boolean {
  const time = Date.parse(iso);
  return Number.isFinite(time) && time > cutoff;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Offset paging with the same shape the backend `Page` responses had. */
export function paginate<T>(all: T[], pageNumber: number, size: number, maxSize: number): Page<T> {
  const page = Math.max(0, Math.floor(pageNumber) || 0);
  const bounded = size > 0 ? Math.min(size, maxSize) : 20;
  const start = page * bounded;
  const content = all.slice(start, start + bounded);
  return {
    content,
    page,
    size: bounded,
    totalElements: all.length,
    totalPages: all.length === 0 ? 0 : Math.ceil(all.length / bounded),
  };
}

/**
 * The timestamp columns the local store is allowed to order by. Every caller
 * carries an ISO date string under one of them, so both are optional: a
 * `FraudAlert` only has `createdAt`, a `FraudEvaluation` only `evaluatedAt`.
 */
export interface SortableTimestamp {
  createdAt?: string;
  evaluatedAt?: string;
}

/**
 * Newest first, with an explicit tie-break. The backend ordered by
 * `createdAt DESC, id DESC`; the local store keeps the same deterministic
 * ordering so paging is stable.
 */
export function byNewestFirst<T extends SortableTimestamp>(
  sortField: 'createdAt' | 'evaluatedAt',
  tiebreak: (item: T) => number | string = () => 0,
): (a: T, b: T) => number {
  return (a, b) => {
    const left = Date.parse(a[sortField] ?? '') || 0;
    const right = Date.parse(b[sortField] ?? '') || 0;
    if (left !== right) {
      return right - left;
    }
    const ta = tiebreak(a);
    const tb = tiebreak(b);
    if (ta === tb) {
      return 0;
    }
    return ta > tb ? -1 : 1;
  };
}

const STATUS_TEXT: Record<number, string> = {
  400: 'Bad Request',
  403: 'Forbidden',
  404: 'Not Found',
  409: 'Conflict',
  422: 'Unprocessable Entity',
  500: 'Internal Server Error',
};

/**
 * Builds the same error shape the backend produced, so `readError(err)` and
 * the `err.status === 404` style checks in components keep working unchanged.
 */
export function demoError(status: number, message: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    statusText: STATUS_TEXT[status] ?? 'Error',
    error: { message, timestamp: new Date().toISOString() },
    url: 'localstorage://nexabank',
  });
}

/** Field-level validation failure, mirroring the backend's `errors` map. */
export function demoValidationError(
  fields: Record<string, string>,
  message = 'Validation failed',
): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 400,
    statusText: STATUS_TEXT[400],
    error: { message, errors: fields, timestamp: new Date().toISOString() },
    url: 'localstorage://nexabank',
  });
}

export const ERRORS = {
  notFound: (what: string, id: number | string) => demoError(404, `${what} not found with id ${id}`),
  badState: (message: string) => demoError(400, message),
  duplicate: (message: string) => demoError(409, message),
  invalid: (message: string) => demoError(422, message),
};
