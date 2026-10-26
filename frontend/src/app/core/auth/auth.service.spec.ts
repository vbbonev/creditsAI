import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let keycloak: {
    hasRealmRole: ReturnType<typeof vi.fn>;
    login: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
    authenticated?: boolean;
    idTokenParsed?: Record<string, unknown>;
  };
  let service: AuthService;

  beforeEach(() => {
    keycloak = {
      hasRealmRole: vi.fn(),
      login: vi.fn().mockResolvedValue(undefined),
      authenticated: true,
      logout: vi.fn().mockResolvedValue(undefined),
      idTokenParsed: { preferred_username: 'admin1' },
    };
    TestBed.configureTestingModule({ providers: [{ provide: Keycloak, useValue: keycloak }] });
    service = TestBed.inject(AuthService);
  });

  it('should report ADMIN when the user has the ADMIN realm role', () => {
    keycloak.hasRealmRole.mockImplementation((role: string) => role === 'ADMIN');
    expect(service.isAdmin()).toBe(true);
    expect(keycloak.hasRealmRole).toHaveBeenCalledWith('ADMIN');
  });

  it('should not report ADMIN for a plain USER', () => {
    keycloak.hasRealmRole.mockImplementation((role: string) => role === 'USER');
    expect(service.isAdmin()).toBe(false);
  });

  it('should return the preferred username from the ID token', () => {
    expect(service.username()).toBe('admin1');
  });

  it('should return an empty username when there is no ID token', () => {
    keycloak.idTokenParsed = undefined;
    expect(service.username()).toBe('');
  });

  it('should log out and return to the start page', async () => {
    await service.logout();
    expect(keycloak.logout).toHaveBeenCalledWith({ redirectUri: `${window.location.origin}/` });
  });

  it('should report a completed login as authenticated', () => {
    expect(service.isAuthenticated()).toBe(true);
  });

  it('should report a failed Keycloak init as not authenticated', () => {
    keycloak.authenticated = undefined;
    expect(service.isAuthenticated()).toBe(false);
  });

  it('should log in and come back to the current page', async () => {
    await service.login();
    expect(keycloak.login).toHaveBeenCalledWith({ redirectUri: window.location.href });
  });
});
