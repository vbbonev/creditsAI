import { Routes } from '@angular/router';
import { CreditList } from './credit-list/credit-list';

/** Routes of the credits feature, lazy-loaded under `/credits`. */
export const creditsRoutes: Routes = [
  { path: '', component: CreditList, title: 'Credits · creditsAI' },
];
