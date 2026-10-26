import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import Keycloak from 'keycloak-js';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  /**
   * Runs the guard for a user with the given authentication state and realm roles.
   * @param authenticated whether the user is logged in
   * @param roles the user's realm roles
   * @returns the guard result
   */
  function runGuard(authenticated: boolean, roles: string[]) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: Keycloak, useValue: { authenticated, realmAccess: { roles } } },
      ],
    });
    return TestBed.runInInjectionContext(() =>
      adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  }

  it('should allow an ADMIN', async () => {
    expect(await runGuard(true, ['USER', 'ADMIN'])).toBe(true);
  });

  it('should redirect a plain USER to the start page', async () => {
    const result = await runGuard(true, ['USER']);
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/');
  });

  it('should redirect an unauthenticated user to the start page', async () => {
    expect(await runGuard(false, ['ADMIN'])).toBeInstanceOf(UrlTree);
  });
});
