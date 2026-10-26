import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

/**
 * Page shown when Keycloak could not be initialized, so the user isn't logged in. It sits outside
 * the shell (no menu, no username) and offers a full reload, which runs the login again.
 */
@Component({
  imports: [MatButtonModule, MatCardModule],
  selector: 'app-auth-unavailable',
  styleUrl: './auth-unavailable.scss',
  templateUrl: './auth-unavailable.html',
})
export class AuthUnavailable {
  /**
   * Reloads the app from the start page, which starts the Keycloak login again.
   */
  protected retry(): void {
    window.location.assign('/');
  }
}
