import { Routes } from '@angular/router';
import { ClientDetail } from './client-detail/client-detail';
import { ClientList } from './client-list/client-list';

/**
 * Routes of the clients feature, lazy-loaded under `/clients`. Creating and editing happen in a
 * dialog, so they have no routes of their own.
 */
export const clientsRoutes: Routes = [
  { path: '', component: ClientList, title: 'Clients · creditsAI' },
  { path: ':id', component: ClientDetail, title: 'Client · creditsAI' },
];
