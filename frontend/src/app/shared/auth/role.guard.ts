import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';

import { Role } from '../models/api.models';
import { AuthStore } from './auth.store';

/**
 * Lets a route (and its lazily loaded area) load only for the given roles. This is for user experience only:
 * every API call is authorised again by the backend.
 */
export function roleGuard(...roles: Role[]): CanMatchFn {
  return () => {
    const auth = inject(AuthStore);
    const router = inject(Router);
    if (!auth.checkExpiry()) {
      return router.createUrlTree(['/login']);
    }
    return auth.hasRole(...roles) ? true : router.createUrlTree([auth.homeUrl()]);
  };
}

/** Only for signed-out users (the login page). */
export const anonymousGuard: CanMatchFn = () => {
  const auth = inject(AuthStore);
  return auth.checkExpiry() ? inject(Router).createUrlTree([auth.homeUrl()]) : true;
};
