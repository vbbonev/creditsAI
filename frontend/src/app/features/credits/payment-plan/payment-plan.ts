import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { CreditCurrency, PaymentPlan as PaymentPlanModel } from '../credit.model';

/**
 * Presentational component (it only displays its inputs) for a payment plan: the totals above a
 * scrolling table with a sticky header and no paginator. Used by the credit form (after
 * Calculate) and the credit detail page.
 */
@Component({
  imports: [CurrencyPipe, DatePipe, MatTableModule],
  selector: 'app-payment-plan',
  styleUrl: './payment-plan.scss',
  templateUrl: './payment-plan.html',
})
export class PaymentPlan {
  /** The plan to show. */
  readonly plan = input.required<PaymentPlanModel>();

  /** Currency of all amounts. */
  readonly currency = input.required<CreditCurrency>();

  /** Table columns, in display order. */
  protected readonly columns = [
    'number',
    'date',
    'payment',
    'principal',
    'interest',
    'remainingBalance',
  ];
}
