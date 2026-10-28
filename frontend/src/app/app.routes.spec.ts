import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import Keycloak from 'keycloak-js';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { provideMaterialDefaults } from './core/material/material.config';

describe('app routes', () => {
  let harness: RouterTestingHarness;

  /**
   * Sets up the real routes with a Keycloak in the given login state.
   * @param authenticated whether Keycloak finished the login
   */
  async function setUp(authenticated: boolean): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideMaterialDefaults(),
        // the client pages load data; requests stay pending in these routing tests
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Keycloak, useValue: { authenticated } },
        {
          provide: AuthService,
          useValue: { username: () => 'user1', isAdmin: () => false, logout: vi.fn() },
        },
      ],
    });
    harness = await RouterTestingHarness.create();
  }

  describe('when logged in', () => {
    beforeEach(() => setUp(true));

    it('should redirect the start page to /clients', async () => {
      await harness.navigateByUrl('/');
      expect(TestBed.inject(Router).url).toBe('/clients');
      expect(harness.routeNativeElement?.textContent).toContain('Clients');
    });

    it.each([
      ['/clients', 'Clients · creditsAI'],
      ['/clients/1', 'Client · creditsAI'],
      ['/credits', 'Credits · creditsAI'],
      ['/credits/new', 'New credit · creditsAI'],
      ['/credits/1', 'Credit · creditsAI'],
    ])('should lazy-load %s inside the shell', async (url, title) => {
      await harness.navigateByUrl(url);
      expect(harness.fixture.nativeElement.querySelector('mat-toolbar')).not.toBeNull();
      expect(TestBed.inject(Title).getTitle()).toBe(title);
    });

    it('should show the not-found page for an unknown URL', async () => {
      await harness.navigateByUrl('/no-such-page');
      expect(harness.routeNativeElement?.textContent).toContain('Page not found');
      expect(TestBed.inject(Title).getTitle()).toBe('Page not found · creditsAI');
    });
  });

  describe('when Keycloak could not log the user in', () => {
    beforeEach(() => setUp(false));

    it('should show the login-unavailable page instead of the shell', async () => {
      await harness.navigateByUrl('/clients');
      expect(TestBed.inject(Router).url).toBe('/unavailable');
      expect(harness.fixture.nativeElement.querySelector('mat-toolbar')).toBeNull();
      expect(harness.routeNativeElement?.textContent).toContain('Login service unavailable');
    });
  });
});
