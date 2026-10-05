import { isPlatformBrowser } from '@angular/common';
import { PLATFORM_ID, inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { IdentityService } from './services/identity.service';

export const authenticatedGuard: CanActivateFn = async () => {
  const identity = inject(IdentityService);
  const router = inject(Router);
  if (!isPlatformBrowser(inject(PLATFORM_ID))) return true;
  if (identity.isAuthenticated() || (await identity.fetchCurrent())) return true;
  return router.createUrlTree(['/login']);
};
