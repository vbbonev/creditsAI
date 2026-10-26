import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  includeBearerTokenInterceptor,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
} from 'keycloak-angular';
import Keycloak from 'keycloak-js';
import { apiBearerTokenCondition } from './auth.config';

describe('apiBearerTokenCondition', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let updateToken: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    updateToken = vi.fn().mockResolvedValue(false);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([includeBearerTokenInterceptor])),
        provideHttpClientTesting(),
        { provide: Keycloak, useValue: { authenticated: true, token: 'test-token', updateToken } },
        { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: [apiBearerTokenCondition] },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  /**
   * Sends a GET request through the interceptor chain and returns the request the backend would see.
   * @param url request URL
   * @returns the intercepted request
   */
  async function send(url: string) {
    http.get(url).subscribe();
    // The interceptor awaits updateToken() before forwarding the request.
    await new Promise((resolve) => setTimeout(resolve));
    const req = httpTesting.expectOne(url);
    req.flush({});
    return req.request;
  }

  it.each(['/api', '/api/clients', '/api/clients?page=0', '/api?x=1'])(
    'should attach the bearer token to %s and refresh it first',
    async (url) => {
      const request = await send(url);
      expect(request.headers.get('Authorization')).toBe('Bearer test-token');
      expect(updateToken).toHaveBeenCalled();
    },
  );

  it.each([
    '/apix',
    '/auth/realms/credits/account',
    'http://localhost/auth/realms/credits/protocol/openid-connect/token',
    '/API/clients',
    'http://localhost/api/clients',
    'https://evil.example.com/api/clients',
    '//evil.example.com/api/clients',
    'https://fonts.googleapis.com/css2?family=Roboto',
  ])('should not attach the bearer token to %s', async (url) => {
    const request = await send(url);
    expect(request.headers.has('Authorization')).toBe(false);
  });
});
