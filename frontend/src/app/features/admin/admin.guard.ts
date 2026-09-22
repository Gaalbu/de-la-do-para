import { isPlatformBrowser } from '@angular/common';
import { PLATFORM_ID, inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { IdentityService } from '../identity/services/identity.service';

export const adminGuard: CanActivateFn = async () => {
  const identity = inject(IdentityService);
  const router = inject(Router);
  const platformId = inject(PLATFORM_ID);

  // Cookie-backed sessions exist only in the browser. Let SSR render the route
  // shell; the client guard and admin API enforce the authenticated role.
  if (!isPlatformBrowser(platformId)) return true;

  if (identity.isAuthenticated()) {
    return identity.isAdmin() ? true : router.createUrlTree(['/login']);
  }

  const account = await identity.fetchCurrent();
  if (account?.role === 'ADMIN') {
    return true;
  }
  return router.createUrlTree(['/login']);
};
