import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

/**
 * Attaches the JWT to outgoing API calls and reacts to 401 responses.
 *
 * - Adds `Authorization: Bearer <token>` to every request aimed at our API.
 * - On `401 Unauthorized` clears the session and redirects to the login page,
 *   preserving the attempted URL so the user returns there after signing in.
 *
 * The backend is still the authority: this only keeps the client in step.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = auth.getToken();
  const isApiRequest = request.url.startsWith(environment.apiBaseUrl);
  const authorized =
    token && isApiRequest
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

  return next(authorized).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && auth.isAuthenticated()) {
        auth.logout();
        void router.navigate(['/login'], {
          queryParams: { returnUrl: router.url },
        });
      }
      return throwError(() => error);
    }),
  );
};
