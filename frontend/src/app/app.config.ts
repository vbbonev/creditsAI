import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { includeBearerTokenInterceptor } from 'keycloak-angular';
import { routes } from './app.routes';
import { provideAuth } from './core/auth/auth.config';
import { errorInterceptor } from './core/http/error.interceptor';
import { provideMaterialDefaults } from './core/material/material.config';

/** Application-wide providers used to bootstrap the root component. */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // errorInterceptor is first (outermost), so it sees the final result of every request
    provideHttpClient(withInterceptors([errorInterceptor, includeBearerTokenInterceptor])),
    provideAuth(),
    provideMaterialDefaults(),
  ],
};
