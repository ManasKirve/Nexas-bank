import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/**
 * Sign-in screen. Posts to `POST /api/v1/auth/login`, stores the returned JWT
 * (via {@link AuthService}) and returns the user to their requested route.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth">
      <section class="auth-card card" aria-labelledby="login-title">
        <div class="brand">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 9.5 12 4l8 5.5M6.5 10.5V19h11v-8.5M10 19v-4.5h4V19" />
            </svg>
          </span>
          <span class="brand-text">
            <span class="brand-name">NexaBank</span>
            <span class="brand-sub">Digital Banking</span>
          </span>
        </div>

        <h1 id="login-title">Sign in</h1>
        <p class="muted">Access your accounts, transfers and fraud monitoring.</p>

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label>
            Username
            <input type="text" formControlName="username" autocomplete="username" placeholder="e.g. customer1" />
          </label>
          @if (form.controls.username.touched && form.controls.username.invalid) {
            <p class="field-error">Username is required.</p>
          }

          <label>
            Password
            <input type="password" formControlName="password" autocomplete="current-password" placeholder="••••••••" />
          </label>
          @if (form.controls.password.touched && form.controls.password.invalid) {
            <p class="field-error">Password is required.</p>
          }

          <button type="submit" class="btn primary block" [disabled]="form.invalid || submitting()">
            {{ submitting() ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>

        @if (error()) {
          <p class="form-error" role="alert">{{ error() }}</p>
        }

        <p class="switch">No account yet? <a routerLink="/signup">Create one</a></p>
      </section>
    </div>
  `,
  styles: [
    `
      .auth {
        display: grid;
        place-items: center;
        min-height: 100vh;
        padding: 2rem 1rem;
        background: var(--nx-bg, #f4f6fb);
      }
      .auth-card {
        width: 100%;
        max-width: 400px;
        padding: 2rem;
        display: grid;
        gap: 0.35rem;
      }
      .brand {
        display: flex;
        align-items: center;
        gap: 0.65rem;
        margin-bottom: 0.75rem;
      }
      .brand-mark {
        display: grid;
        place-items: center;
        width: 40px;
        height: 40px;
        border-radius: var(--nx-r-md, 10px);
        background: var(--nx-accent-soft, #e6efff);
        color: var(--nx-accent, #2563eb);
      }
      .brand-mark svg {
        width: 22px;
        height: 22px;
      }
      .brand-text {
        display: grid;
      }
      .brand-name {
        font-weight: 750;
        letter-spacing: -0.02em;
      }
      .brand-sub {
        font-size: 0.6875rem;
        color: var(--nx-muted, #64748b);
      }
      h1 {
        margin: 0;
      }
      form {
        display: grid;
        gap: 0.35rem;
        margin-top: 0.75rem;
      }
      .block {
        width: 100%;
        justify-content: center;
        margin-top: 0.5rem;
      }
      .switch {
        margin-top: 1rem;
        font-size: 0.8125rem;
        color: var(--nx-muted, #64748b);
        text-align: center;
      }
    `,
  ],
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login({ username: username.trim(), password }).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';
        this.submitting.set(false);
        void this.router.navigateByUrl(returnUrl);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(
          err.status === 401
            ? 'Invalid username or password.'
            : err.status === 0
              ? 'Cannot reach the backend. Make sure the API is running and environment.apiBaseUrl is correct.'
              : 'Sign-in failed. Please try again.',
        );
        this.submitting.set(false);
      },
    });
  }
}
