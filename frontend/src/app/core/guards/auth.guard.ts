import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Role } from '../../shared/models/auth.model';

/**
 * Requires a valid session. Unauthenticated users are sent to `/login`
 * with a `returnUrl` so they land back on the requested screen afterwards.
 */
export const authGuard: CanActivateFn = (
  _route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/**
 * Requires a session *and* one of the roles declared in the route's
 * `data.roles`. This mirrors the backend's RBAC; the server enforces the same
 * rules regardless, so hiding a route is convenience, not security.
 */
export const roleGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  const required = (route.data['roles'] as Role[] | undefined) ?? [];
  if (required.length === 0 || auth.hasAnyRole(...required)) {
    return true;
  }
  return router.createUrlTree(['/dashboard']);
};
