import { Routes } from '@angular/router';
import { adminGuard } from '../../core/auth/admin.guard';
import { CreditDetail } from './credit-detail/credit-detail';
import { CreditForm } from './credit-form/credit-form';
import { CreditList } from './credit-list/credit-list';

/**
 * Routes of the credits feature, lazy-loaded under `/credits`. `new` must come before `:id`, and
 * editing is for ADMIN only (the backend enforces it as well).
 */
export const creditsRoutes: Routes = [
  { path: '', component: CreditList, title: 'Credits · creditsAI' },
  { path: 'new', component: CreditForm, title: 'New credit · creditsAI' },
  { path: ':id', component: CreditDetail, title: 'Credit · creditsAI' },
  {
    path: ':id/edit',
    component: CreditForm,
    canActivate: [adminGuard],
    title: 'Edit credit · creditsAI',
  },
];
