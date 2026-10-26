import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { createAuthGuard } from 'keycloak-angular';

/**
 * Route guard for every page in the app. keycloak-angular swallows a failed Keycloak init and
 * starts the app logged out; this guard then shows the "login service unavailable" page instead of
 * pages whose API calls would all fail.
 */
export const authenticatedGuard = createAuthGuard<CanActivateFn>(
  async (_route, _state, { authenticated }) => {
    const router = inject(Router);
    return authenticated ? true : router.parseUrl('/unavailable');
  },
);
