import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return password && confirm && password !== confirm ? { passwordMismatch: true } : null;
}

/**
 * Public registration screen. Posts to `POST /api/v1/auth/register`
 * (always creates a CUSTOMER) and then signs the new user in.
 */
@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth">
      <section class="auth-card card" aria-labelledby="signup-title">
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

        <h1 id="signup-title">Create account</h1>
        <p class="muted">Register a customer profile and start banking.</p>

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label>
            Username
            <input type="text" formControlName="username" autocomplete="username" placeholder="3–64 characters" />
          </label>
          @if (form.controls.username.touched && form.controls.username.invalid) {
            <p class="field-error">Username must be 3–64 characters.</p>
          }

          <label>
            Email
            <input type="email" formControlName="email" autocomplete="email" placeholder="you@example.com" />
          </label>
          @if (form.controls.email.touched && form.controls.email.invalid) {
            <p class="field-error">Enter a valid email address.</p>
          }

          <label>
            Password
            <input type="password" formControlName="password" autocomplete="new-password" placeholder="At least 8 characters" />
          </label>
          @if (form.controls.password.touched && form.controls.password.invalid) {
            <p class="field-error">Password must be 8–128 characters.</p>
          }

          <label>
            Confirm password
            <input type="password" formControlName="confirmPassword" autocomplete="new-password" placeholder="Repeat password" />
          </label>
          @if (form.errors?.['passwordMismatch'] && form.controls.confirmPassword.touched) {
            <p class="field-error">Passwords do not match.</p>
          }

          <button type="submit" class="btn primary block" [disabled]="form.invalid || submitting()">
            {{ submitting() ? 'Creating…' : 'Create account' }}
          </button>
        </form>

        @if (error()) {
          <p class="form-error" role="alert">{{ error() }}</p>
        }

        <p class="switch">Already have an account? <a routerLink="/login">Sign in</a></p>
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
        max-width: 420px;
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
export class SignupComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = new FormGroup(
    {
      username: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(3), Validators.maxLength(64)],
      }),
      email: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.email, Validators.maxLength(255)],
      }),
      password: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(8), Validators.maxLength(128)],
      }),
      confirmPassword: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required],
      }),
    },
    { validators: passwordsMatch },
  );

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { username, email, password } = this.form.getRawValue();
    const credentials = { username: username.trim(), password };

    this.auth.register({ username: username.trim(), email: email.trim(), password }).subscribe({
      next: () => {
        // Registration returns the profile but no token, so sign in right away.
        this.auth.login(credentials).subscribe({
          next: () => {
            this.submitting.set(false);
            void this.router.navigate(['/dashboard']);
          },
          error: () => {
            this.submitting.set(false);
            void this.router.navigate(['/login'], { queryParams: { registered: '1' } });
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as { message?: string; errors?: Record<string, string> } | undefined;
        if (body?.errors) {
          this.error.set(
            Object.entries(body.errors)
              .map(([field, message]) => `${field}: ${message}`)
              .join(' '),
          );
        } else if (body?.message) {
          this.error.set(body.message);
        } else if (err.status === 0) {
          this.error.set('Cannot reach the backend. Make sure the API is running.');
        } else {
          this.error.set('Registration failed. Please try again.');
        }
        this.submitting.set(false);
      },
    });
  }
}
