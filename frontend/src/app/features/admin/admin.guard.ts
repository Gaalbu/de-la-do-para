import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { IdentityService } from '../identity/services/identity.service';

export const adminGuard: CanActivateFn = async () => {
  const identity = inject(IdentityService);
  const router = inject(Router);

  if (identity.isAuthenticated()) {
    return identity.isAdmin() ? true : router.createUrlTree(['/login']);
  }

  const account = await identity.fetchCurrent();
  if (account?.role === 'ADMIN') {
    return true;
  }
  return router.createUrlTree(['/login']);
};
