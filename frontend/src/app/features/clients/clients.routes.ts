import { Routes } from '@angular/router';
import { ClientList } from './client-list/client-list';

/** Routes of the clients feature, lazy-loaded under `/clients`. */
export const clientsRoutes: Routes = [
  { path: '', component: ClientList, title: 'Clients · creditsAI' },
];
