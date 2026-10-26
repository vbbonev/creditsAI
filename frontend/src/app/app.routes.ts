import { Routes } from '@angular/router';
import { authenticatedGuard } from './core/auth/authenticated.guard';
import { AuthUnavailable } from './core/layout/auth-unavailable/auth-unavailable';
import { NotFound } from './core/layout/not-found/not-found';
import { Shell } from './core/layout/shell/shell';

/**
 * Top-level routes. Every page renders inside the {@link Shell} layout and requires a completed
 * login; each feature is lazy-loaded with `loadChildren`. `/unavailable` comes first, because the
 * shell's `**` would match it too.
 */
export const routes: Routes = [
  {
    path: 'unavailable',
    component: AuthUnavailable,
    title: 'Login service unavailable · creditsAI',
  },
  {
    path: '',
    component: Shell,
    canActivate: [authenticatedGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'clients' },
      {
        path: 'clients',
        loadChildren: () =>
          import('./features/clients/clients.routes').then((m) => m.clientsRoutes),
      },
      {
        path: 'credits',
        loadChildren: () =>
          import('./features/credits/credits.routes').then((m) => m.creditsRoutes),
      },
      { path: '**', component: NotFound, title: 'Page not found · creditsAI' },
    ],
  },
];
