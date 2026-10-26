import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../auth/auth.service';

/** A link in the top menu. */
interface NavLink {
  /** Route path the link opens. */
  path: string;
  /** Visible label. */
  label: string;
  /** Material Symbols icon name. */
  icon: string;
}

/**
 * Application layout: a top toolbar with the horizontal menu, the current user and logout,
 * and the routed page below it. Used as the parent route of every page.
 */
@Component({
  imports: [
    MatButtonModule,
    MatIconModule,
    MatToolbarModule,
    MatTooltipModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  selector: 'app-shell',
  styleUrl: './shell.scss',
  templateUrl: './shell.html',
})
export class Shell {
  private readonly auth = inject(AuthService);

  /** Entries of the top menu. Credits have their own list, not nested under a client. */
  protected readonly links: NavLink[] = [
    { path: '/clients', label: 'Clients', icon: 'group' },
    { path: '/credits', label: 'Credits', icon: 'credit_card' },
  ];

  /** Login name of the current user. */
  protected readonly username = this.auth.username();

  /** Whether the current user has the ADMIN role (only changes the display; the backend enforces it). */
  protected readonly isAdmin = this.auth.isAdmin();

  /**
   * Logs the user out of Keycloak.
   */
  protected logout(): void {
    void this.auth.logout();
  }
}
