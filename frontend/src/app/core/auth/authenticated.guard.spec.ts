import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import Keycloak from 'keycloak-js';
import { authenticatedGuard } from './authenticated.guard';

describe('authenticatedGuard', () => {
  /**
   * Runs the guard with the given Keycloak login state.
   * @param authenticated whether Keycloak finished the login
   * @returns the guard result
   */
  function runGuard(authenticated: boolean) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: Keycloak, useValue: { authenticated } }],
    });
    return TestBed.runInInjectionContext(() =>
      authenticatedGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  }

  it('should allow a logged-in user', async () => {
    expect(await runGuard(true)).toBe(true);
  });

  it('should send the user to /unavailable when Keycloak could not log them in', async () => {
    const result = await runGuard(false);
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/unavailable');
  });
});
