import { EnvironmentProviders } from '@angular/core';
import {
  createInterceptorCondition,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  IncludeBearerTokenCondition,
  provideKeycloak,
} from 'keycloak-angular';
import { environment } from '../../../environments/environment';
import { API_URL_PATTERN } from '../http/api-url';

/**
 * Attaches the bearer token only to API requests ({@link API_URL_PATTERN}), so the token never leaks
 * to Keycloak, fonts or any other host.
 */
export const apiBearerTokenCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: API_URL_PATTERN,
});

/**
 * Sets up keycloak-angular: the whole app requires login (authorization code flow with PKCE S256),
 * and `includeBearerTokenInterceptor` is configured with {@link apiBearerTokenCondition}.
 * @returns the environment providers to add to `appConfig`
 */
export function provideAuth(): EnvironmentProviders {
  return provideKeycloak({
    config: environment.keycloak,
    initOptions: {
      onLoad: 'login-required',
      pkceMethod: 'S256',
    },
    providers: [
      { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: [apiBearerTokenCondition] },
    ],
  });
}
