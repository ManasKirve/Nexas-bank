import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthUser,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  Role,
} from '../../shared/models/auth.model';

/** Persistence keys. Only the JWT session lives in storage — no banking data. */
const TOKEN_KEY = 'nexabank_access_token';
const USER_KEY = 'nexabank_current_user';

function readStoredUser(): AuthUser | null {
  if (typeof localStorage === 'undefined') {
    return null;
  }
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

/**
 * Owns the JWT session: login, registration, the cached current user and logout.
 *
 * The access token is attached to every API request by {@link authInterceptor};
 * the backend remains the real authorization boundary. Roles come from the JWT's
 * `role` claim (surfaced by `LoginResponse.user`) and only drive client-side
 * navigation/guards.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  private readonly _user = signal<AuthUser | null>(readStoredUser());

  /** Currently signed-in user, or `null` when there is no valid session. */
  readonly user = this._user.asReadonly();

  readonly isAuthenticated = computed(() => this._user() !== null && this.getToken() !== null);

  readonly role = computed<Role | null>(() => this._user()?.role ?? null);

  /** Display name for the shell. Falls back to the username. */
  readonly displayName = computed(() => this._user()?.username ?? '');

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.base}/api/v1/auth/login`, request)
      .pipe(tap((response) => this.storeSession(response)));
  }

  /** Public registration always creates a CUSTOMER account on the backend. */
  register(request: RegisterRequest): Observable<AuthUser> {
    return this.http.post<AuthUser>(`${this.base}/api/v1/auth/register`, request);
  }

  /** Re-fetches the profile for the current token (also validates the session). */
  me(): Observable<AuthUser> {
    return this.http
      .get<AuthUser>(`${this.base}/api/v1/auth/me`)
      .pipe(tap((user) => this.persistUser(user)));
  }

  /** Stateless backend: logout just discards the client-side token. */
  logout(): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    }
    this._user.set(null);
  }

  getToken(): string | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }
    return localStorage.getItem(TOKEN_KEY);
  }

  hasAnyRole(...roles: Role[]): boolean {
    const current = this.role();
    return current !== null && roles.includes(current);
  }

  private storeSession(response: LoginResponse): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(TOKEN_KEY, response.accessToken);
    }
    this.persistUser(response.user);
  }

  private persistUser(user: AuthUser): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(USER_KEY, JSON.stringify(user));
    }
    this._user.set(user);
  }
}
