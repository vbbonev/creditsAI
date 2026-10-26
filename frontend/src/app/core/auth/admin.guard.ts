import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { createAuthGuard } from 'keycloak-angular';
import { ADMIN_ROLE } from './roles';

/**
 * Route guard for ADMIN-only pages (e.g. edit forms), built with keycloak-angular's `createAuthGuard`.
 * Users without the role are sent to the start page. The backend still enforces the role.
 */
export const adminGuard = createAuthGuard<CanActivateFn>(
  async (_route, _state, { authenticated, grantedRoles }) => {
    const router = inject(Router);
    return authenticated && grantedRoles.realmRoles.includes(ADMIN_ROLE)
      ? true
      : router.parseUrl('/');
  },
);
