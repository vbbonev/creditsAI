import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PaymentPlan as PaymentPlanModel } from '../credit.model';
import { PaymentPlan } from './payment-plan';

describe('PaymentPlan', () => {
  let fixture: ComponentFixture<PaymentPlan>;
  let el: HTMLElement;

  const plan: PaymentPlanModel = {
    termMonths: 2,
    monthlyPayment: 5012.5,
    totalInterest: 25,
    totalPaid: 10025,
    rows: [
      {
        number: 1,
        date: '2026-02-15',
        payment: 5012.5,
        principal: 5000,
        interest: 12.5,
        remainingBalance: 5000,
      },
      {
        number: 2,
        date: '2026-03-15',
        payment: 5012.5,
        principal: 5000,
        interest: 12.5,
        remainingBalance: 0,
      },
    ],
  };

  /**
   * Renders the plan in the given currency.
   * @param currency the currency of the amounts
   */
  async function render(currency: 'EUR' | 'USD' | 'CHF' = 'EUR'): Promise<void> {
    fixture = TestBed.createComponent(PaymentPlan);
    fixture.componentRef.setInput('plan', plan);
    fixture.componentRef.setInput('currency', currency);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  }

  it('should show the totals with the currency symbol', async () => {
    await render();
    expect(el.querySelector('.term')?.textContent).toContain('2 months');
    expect(el.querySelector('.monthly')?.textContent).toContain('€5,012.50');
    expect(el.querySelector('.interest')?.textContent).toContain('€25.00');
    expect(el.querySelector('.paid')?.textContent).toContain('€10,025.00');
  });

  it('should use the credit currency', async () => {
    await render('USD');
    expect(el.querySelector('.monthly')?.textContent).toContain('$5,012.50');
  });

  it('should show the columns of the plan', async () => {
    await render();
    const headers = Array.from(el.querySelectorAll('th')).map((th) => th.textContent!.trim());
    expect(headers).toEqual(['#', 'Date', 'Payment', 'Principal', 'Interest', 'Remaining balance']);
  });

  it('should show one row per instalment with dd.MM.yyyy dates', async () => {
    await render();
    const rows = el.querySelectorAll('tr.mat-mdc-row');
    expect(rows).toHaveLength(2);
    const cells = Array.from(rows[0].querySelectorAll('td')).map((td) => td.textContent!.trim());
    expect(cells).toEqual(['1', '15.02.2026', '€5,012.50', '€5,000.00', '€12.50', '€5,000.00']);
  });

  it('should scroll inside a container with a sticky header and have no paginator', async () => {
    await render();
    expect(el.querySelector('.scroll')).not.toBeNull();
    expect(el.querySelector('th.mat-mdc-table-sticky')).not.toBeNull();
    expect(el.querySelector('mat-paginator')).toBeNull();
  });
});
