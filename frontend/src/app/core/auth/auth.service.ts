import { inject, Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';
import { ADMIN_ROLE } from './roles';

/**
 * The single place for role checks and session actions in the UI (a Facade over keycloak-js).
 * Hiding a button is only for user experience: the backend still enforces every role.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = inject(Keycloak);

  /**
   * Checks whether Keycloak finished the login. With `login-required` this is only false when
   * Keycloak could not be initialized (e.g. the login service is down).
   * @returns true if the user is logged in
   */
  isAuthenticated(): boolean {
    return this.keycloak.authenticated ?? false;
  }

  /**
   * Redirects to the Keycloak login and returns to the current page afterwards.
   * @returns promise that resolves once the redirect to Keycloak has started
   */
  login(): Promise<void> {
    return this.keycloak.login({ redirectUri: window.location.href });
  }

  /**
   * Checks whether the logged-in user has the realm role `ADMIN`.
   * @returns true if the user may edit and archive records
   */
  isAdmin(): boolean {
    return this.keycloak.hasRealmRole(ADMIN_ROLE);
  }

  /**
   * Reads the login name of the current user from the ID token.
   * @returns the `preferred_username` claim, or an empty string if it is missing
   */
  username(): string {
    return this.keycloak.idTokenParsed?.['preferred_username'] ?? '';
  }

  /**
   * Ends the Keycloak session and returns to the app's start page, which asks for login again.
   * @returns promise that resolves once the redirect to Keycloak has started
   */
  logout(): Promise<void> {
    return this.keycloak.logout({ redirectUri: `${window.location.origin}/` });
  }
}
