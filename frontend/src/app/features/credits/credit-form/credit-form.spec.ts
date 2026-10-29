import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { provideMaterialDefaults } from '../../../core/material/material.config';
import { NotificationService } from '../../../shared/notification/notification.service';
import { Credit, CreditRequest, PaymentPlan } from '../credit.model';
import { CreditService } from '../credit.service';
import { CLIENT_SEARCH_DEBOUNCE_MS, CreditForm } from './credit-form';

describe('CreditForm', () => {
  let fixture: ComponentFixture<CreditForm>;
  let el: HTMLElement;
  let creditService: {
    get: ReturnType<typeof vi.fn>;
    calculate: ReturnType<typeof vi.fn>;
    createWithFiles: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    searchClients: ReturnType<typeof vi.fn>;
  };
  let notification: { success: ReturnType<typeof vi.fn> };
  let navigate: ReturnType<typeof vi.spyOn>;

  const acme = { id: 3, name: 'Acme', identifier: '123' };
  const plan: PaymentPlan = {
    termMonths: 12,
    monthlyPayment: 856.07,
    totalInterest: 272.84,
    totalPaid: 10272.84,
    rows: [
      {
        number: 1,
        date: '2026-02-15',
        payment: 856.07,
        principal: 803.57,
        interest: 52.5,
        remainingBalance: 9196.43,
      },
    ],
  };
  const saved: Credit = {
    id: 9,
    creditNumber: 'CR-2026-000009',
    client: acme,
    type: 'MORTGAGE',
    amount: 10000,
    currency: 'EUR',
    interestRate: 5.25,
    startDate: '2026-01-15',
    endDate: '2027-01-15',
    hasCollateral: false,
    collateralValue: null,
    collateralDescription: null,
    status: 'INACTIVE',
    archived: false,
    archivedAt: null,
    createdAt: '2026-10-27T10:00:00Z',
    updatedAt: '2026-10-27T10:00:00Z',
    plan,
  };

  /**
   * Renders the form page.
   * @param id the credit id of the edit route; omitted for the new-credit route
   * @param error what `get` fails with, instead of returning the credit
   * @param credit the credit `get` returns
   */
  async function render(
    id?: string,
    error?: HttpErrorResponse,
    credit: Credit = saved,
  ): Promise<void> {
    creditService = {
      get: vi.fn().mockReturnValue(error ? throwError(() => error) : of(credit)),
      calculate: vi.fn().mockReturnValue(of(plan)),
      createWithFiles: vi.fn().mockReturnValue(of(saved)),
      update: vi.fn().mockReturnValue(of(saved)),
      searchClients: vi.fn().mockReturnValue(of([acme])),
    };
    notification = { success: vi.fn() };
    TestBed.configureTestingModule({
      imports: [CreditForm],
      providers: [
        provideRouter([]),
        provideMaterialDefaults(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(id === undefined ? {} : { id }) },
          },
        },
        { provide: CreditService, useValue: creditService },
        { provide: NotificationService, useValue: notification },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(CreditForm);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  }

  /**
   * Finds a form control by its `name`.
   * @param name the control name
   * @returns the element, or null if it is not shown
   */
  function control<T extends HTMLElement = HTMLInputElement>(name: string): T | null {
    return el.querySelector<T>(`[name="${name}"]`);
  }

  /**
   * Types into a text, number or date input.
   * @param name the control name
   * @param value the text to type
   */
  async function type(name: string, value: string): Promise<void> {
    const input = control(name)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  /**
   * Chooses an option of a `mat-select`.
   * @param name the control name
   * @param label the visible option text
   */
  async function choose(name: string, label: string): Promise<void> {
    control(name)!.click();
    await fixture.whenStable();
    Array.from(document.querySelectorAll<HTMLElement>('mat-option'))
      .find((o) => o.textContent!.trim() === label)!
      .click();
    await fixture.whenStable();
  }

  /**
   * Types a search text into the client box and picks the first suggestion.
   * @param text the text to type
   */
  async function pickClient(text = 'Ac'): Promise<void> {
    const input = control('clientId')!;
    input.focus();
    vi.useFakeTimers();
    input.value = text;
    input.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(CLIENT_SEARCH_DEBOUNCE_MS);
    vi.useRealTimers();
    await fixture.whenStable();
    document.querySelector<HTMLElement>('mat-option')!.click();
    await fixture.whenStable();
  }

  /**
   * Fills every required input with valid values (a 12-month credit, no collateral).
   */
  async function fillValid(): Promise<void> {
    await pickClient();
    await choose('type', 'Mortgage');
    await type('amount', '10000');
    await type('interestRate', '5.25');
    await type('startDate', '15.01.2026');
    await type('endDate', '15.01.2027');
  }

  /**
   * Finds a button by its visible label.
   * @param label the button text (without the icon)
   * @returns the button
   */
  function button(label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent!.includes(label))!;
  }

  /**
   * Reads where the Cancel link goes.
   * @returns the `href` of the Cancel link
   */
  function cancelHref(): string | null {
    return el.querySelector('mat-card-actions a')!.getAttribute('href');
  }

  /**
   * Presses Calculate and waits for the plan.
   */
  async function calculate(): Promise<void> {
    button('Calculate').click();
    await fixture.whenStable();
  }

  /**
   * Reads the visible error messages of one control's form field.
   * @param name the control name
   * @returns the trimmed texts of its `mat-error`s
   */
  function errors(name: string): string[] {
    return Array.from(control(name)!.closest('mat-form-field')!.querySelectorAll('mat-error')).map(
      (e) => e.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  /**
   * Marks a control as touched so its errors show.
   * @param name the control name
   */
  async function touch(name: string): Promise<void> {
    control(name)!.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  }

  afterEach(() => vi.useRealTimers());

  describe('new credit', () => {
    beforeEach(() => render());

    it('should show the heading and start with Calculate and Save disabled and no plan', () => {
      expect(el.querySelector('mat-card-title')?.textContent).toContain('New credit');
      expect(button('Calculate').disabled).toBe(true);
      expect(button('Save').disabled).toBe(true);
      expect(el.querySelector('app-payment-plan')).toBeNull();
    });

    it('should default the currency to EUR', () => {
      expect(control('currency')!.textContent).toContain('EUR');
    });

    it('should search clients after the user stops typing and show "name (identifier)"', async () => {
      const input = control('clientId')!;
      input.focus();
      vi.useFakeTimers();
      input.value = ' Ac ';
      input.dispatchEvent(new Event('input'));
      vi.advanceTimersByTime(CLIENT_SEARCH_DEBOUNCE_MS - 1);
      expect(creditService.searchClients).not.toHaveBeenCalled();
      vi.advanceTimersByTime(1);
      vi.useRealTimers();
      await fixture.whenStable();

      expect(creditService.searchClients).toHaveBeenCalledWith('Ac');
      expect(document.querySelector('mat-option')?.textContent?.trim()).toBe('Acme (123)');
    });

    it('should show the picked client as "name (identifier)"', async () => {
      await pickClient();
      expect(control('clientId')!.value).toBe('Acme (123)');
      expect(errors('clientId')).toEqual([]);
    });

    it('should keep the form invalid while the client is only typed, not picked', async () => {
      await type('clientId', 'Acme');
      await touch('clientId');
      expect(errors('clientId')).toEqual(['Pick a client from the list.']);
    });

    describe('validation', () => {
      it('should require the amount and reject zero, too large and too many decimals', async () => {
        await touch('amount');
        expect(errors('amount')).toEqual(['Amount is required.']);
        await type('amount', '0');
        expect(errors('amount')).toEqual(['Amount must be greater than zero.']);
        await type('amount', '100000001');
        expect(errors('amount')).toEqual(['Amount must be at most 100000000.']);
        await type('amount', '10.123');
        expect(errors('amount')).toEqual(['Amount may have at most 2 decimals.']);
        await type('amount', '100000000');
        expect(errors('amount')).toEqual([]);
        await type('amount', '0.01');
        expect(errors('amount')).toEqual([]);
      });

      it('should accept a rate of 0 to 100 with up to 4 decimals only', async () => {
        await touch('interestRate');
        expect(errors('interestRate')).toEqual(['Interest rate is required.']);
        for (const bad of ['-0.1', '100.01']) {
          await type('interestRate', bad);
          expect(errors('interestRate')).toEqual([
            'Interest rate must be between 0 and 100 percent.',
          ]);
        }
        await type('interestRate', '5.12345');
        expect(errors('interestRate')).toEqual(['Interest rate may have at most 4 decimals.']);
        for (const good of ['0', '100', '5.1234']) {
          await type('interestRate', good);
          expect(errors('interestRate')).toEqual([]);
        }
      });

      it('should reject a start date before 01.01.1900 and accept that day', async () => {
        await type('startDate', '31.12.1899');
        await touch('startDate');
        expect(errors('startDate')).toEqual(['Start date must be on or after 01.01.1900.']);
        await type('startDate', '01.01.1900');
        expect(errors('startDate')).toEqual([]);
      });

      it('should reject an end date after 31.12.2200 and accept that day', async () => {
        await type('endDate', '01.01.2201');
        await touch('endDate');
        expect(errors('endDate')).toEqual(['End date must be on or before 31.12.2200.']);
        await type('endDate', '31.12.2200');
        expect(errors('endDate')).not.toContain('End date must be on or before 31.12.2200.');
      });

      it('should reject text that is not a dd.MM.yyyy date', async () => {
        await type('startDate', '2026-01-15');
        await touch('startDate');
        expect(errors('startDate')).toEqual(['Enter a date as dd.MM.yyyy.']);
      });

      it('should require the end to be after the start', async () => {
        await type('startDate', '15.01.2026');
        await type('endDate', '15.01.2026');
        await touch('endDate');
        expect(errors('endDate')).toEqual(['End date must be after the start date.']);
      });

      it('should require a whole number of months on the same day of the month', async () => {
        await type('startDate', '15.01.2026');
        await type('endDate', '16.02.2026');
        await touch('endDate');
        expect(errors('endDate')).toEqual([
          'End date must be a whole number of months after the start date, on the same day of the month.',
        ]);
        await type('endDate', '15.02.2026');
        expect(errors('endDate')).toEqual([]);
      });

      it('should accept month-end clamping (31.01 to 28.02)', async () => {
        await type('startDate', '31.01.2026');
        await type('endDate', '28.02.2026');
        await touch('endDate');
        expect(errors('endDate')).toEqual([]);
      });

      it('should re-check the end date when the start date changes', async () => {
        await type('startDate', '15.01.2026');
        await type('endDate', '15.02.2026');
        await touch('endDate');
        expect(errors('endDate')).toEqual([]);
        await type('startDate', '16.01.2026');
        expect(errors('endDate')).toEqual([
          'End date must be a whole number of months after the start date, on the same day of the month.',
        ]);
      });

      it('should limit the term to 480 months', async () => {
        await type('startDate', '01.01.2000');
        await type('endDate', '01.01.2040');
        await touch('endDate');
        expect(errors('endDate')).toEqual([]);
        await type('endDate', '01.02.2040');
        expect(errors('endDate')).toEqual(['The term must be between 1 and 480 months.']);
      });
    });

    describe('calculate and save', () => {
      it('should enable Calculate once the plan inputs are valid, even without a client', async () => {
        await type('amount', '10000');
        await type('interestRate', '5.25');
        await type('startDate', '15.01.2026');
        expect(button('Calculate').disabled).toBe(true);
        await type('endDate', '15.01.2027');
        expect(button('Calculate').disabled).toBe(false);
        expect(button('Save').disabled).toBe(true);
      });

      it('should send the plan inputs, with dates as yyyy-MM-dd, and show the plan and totals', async () => {
        await fillValid();
        await calculate();

        expect(creditService.calculate).toHaveBeenCalledWith({
          amount: 10000,
          currency: 'EUR',
          interestRate: 5.25,
          startDate: '2026-01-15',
          endDate: '2027-01-15',
        });
        const shown = el.querySelector('app-payment-plan')!;
        expect(shown.querySelector('.term')?.textContent).toContain('12 months');
        expect(shown.querySelector('.monthly')?.textContent).toContain('€856.07');
        expect(shown.querySelectorAll('tr.mat-mdc-row')).toHaveLength(1);
      });

      it('should enable Save only after a successful Calculate', async () => {
        await fillValid();
        expect(button('Save').disabled).toBe(true);
        await calculate();
        expect(button('Save').disabled).toBe(false);
      });

      it('should keep Save disabled when Calculate fails', async () => {
        await fillValid();
        creditService.calculate.mockReturnValue(
          throwError(() => new HttpErrorResponse({ status: 500 })),
        );
        await calculate();
        expect(el.querySelector('app-payment-plan')).toBeNull();
        expect(button('Save').disabled).toBe(true);
        expect(button('Calculate').disabled).toBe(false);
      });

      it.each([
        ['amount', '20000'],
        ['interestRate', '6'],
        ['startDate', '16.01.2026'],
        ['endDate', '15.02.2027'],
      ])('should clear the plan and disable Save when %s changes', async (name, value) => {
        await fillValid();
        await calculate();
        expect(button('Save').disabled).toBe(false);

        await type(name, value);

        expect(el.querySelector('app-payment-plan')).toBeNull();
        expect(button('Save').disabled).toBe(true);
      });

      it('should clear the plan when the type, currency or client changes', async () => {
        await fillValid();
        await calculate();
        await choose('type', 'Auto');
        expect(el.querySelector('app-payment-plan')).toBeNull();

        await calculate();
        expect(el.querySelector('app-payment-plan')).not.toBeNull();
        await choose('currency', 'CHF');
        expect(el.querySelector('app-payment-plan')).toBeNull();

        await calculate();
        expect(el.querySelector('app-payment-plan')).not.toBeNull();
        await type('clientId', 'Acm');
        expect(el.querySelector('app-payment-plan')).toBeNull();
        expect(button('Save').disabled).toBe(true);
      });

      it('should clear the plan when the collateral box or a collateral field changes', async () => {
        await fillValid();
        await calculate();
        control('hasCollateral')!.querySelector<HTMLInputElement>('input')!.click();
        await fixture.whenStable();
        expect(el.querySelector('app-payment-plan')).toBeNull();

        await type('collateralValue', '5000');
        await type('collateralDescription', 'A car');
        await calculate();
        expect(el.querySelector('app-payment-plan')).not.toBeNull();
        await type('collateralValue', '6000');
        expect(el.querySelector('app-payment-plan')).toBeNull();
      });

      it('should save the inputs only (no plan) and open the new credit', async () => {
        await fillValid();
        await calculate();

        button('Save').click();
        await fixture.whenStable();

        const request: CreditRequest = {
          clientId: 3,
          type: 'MORTGAGE',
          amount: 10000,
          currency: 'EUR',
          interestRate: 5.25,
          startDate: '2026-01-15',
          endDate: '2027-01-15',
          hasCollateral: false,
          collateralValue: null,
          collateralDescription: null,
        };
        expect(creditService.createWithFiles).toHaveBeenCalledWith(request, []);
        expect(creditService.update).not.toHaveBeenCalled();
        expect(notification.success).toHaveBeenCalledWith('Credit CR-2026-000009 was created.');
        expect(navigate).toHaveBeenCalledWith(['/credits', 9]);
      });

      it('should show the backend field messages on the matching controls and stay', async () => {
        await fillValid();
        await calculate();
        creditService.createWithFiles.mockReturnValue(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 400,
                error: {
                  detail: 'Some fields are invalid.',
                  errors: { amount: 'Amount must be at most 5.', clientId: 'Client is required.' },
                },
              }),
          ),
        );

        button('Save').click();
        await fixture.whenStable();

        expect(errors('amount')).toEqual(['Amount must be at most 5.']);
        expect(errors('clientId')).toEqual(['Client is required.']);
        expect(navigate).not.toHaveBeenCalled();
        expect(cancelHref()).toBe('/credits');
      });

      it('should show the backend messages of a failed calculation on the controls', async () => {
        await fillValid();
        creditService.calculate.mockReturnValue(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 400,
                error: { errors: { endDate: 'End date must be after the start date.' } },
              }),
          ),
        );

        await calculate();

        expect(errors('endDate')).toEqual(['End date must be after the start date.']);
      });
    });

    describe('files', () => {
      const pdf = new File(['x'], 'a.pdf', { type: 'application/pdf' });

      /**
       * Simulates choosing files in the picker.
       * @param files the files to choose
       */
      async function pick(files: File[]): Promise<void> {
        const input = control('files')!;
        Object.defineProperty(input, 'files', { value: files, configurable: true });
        input.dispatchEvent(new Event('change'));
        await fixture.whenStable();
      }

      it('should send the chosen files with the first save', async () => {
        await fillValid();
        await calculate();
        await pick([pdf]);

        button('Save').click();
        await fixture.whenStable();

        expect(creditService.createWithFiles).toHaveBeenCalledTimes(1);
        expect(creditService.createWithFiles.mock.calls[0][1]).toEqual([pdf]);
      });

      it('should keep the calculated plan when files are added or removed', async () => {
        await fillValid();
        await calculate();
        await pick([pdf]);
        expect(el.querySelector('app-payment-plan')).not.toBeNull();
        expect(button('Save').disabled).toBe(false);

        el.querySelector<HTMLButtonElement>('button[aria-label="Remove a.pdf"]')!.click();
        await fixture.whenStable();
        expect(el.querySelector('app-payment-plan')).not.toBeNull();
        expect(button('Save').disabled).toBe(false);
      });

      it('should block Save and explain when a file is not allowed', async () => {
        await fillValid();
        await calculate();
        await pick([new File(['x'], 'virus.exe')]);

        expect(el.querySelector('.file-errors')!.textContent).toContain('not allowed');
        expect(button('Save').disabled).toBe(true);
        expect(creditService.createWithFiles).not.toHaveBeenCalled();
      });

      it('should block Save above 30 files and name a credit in the message', async () => {
        await fillValid();
        await calculate();
        await pick(Array.from({ length: 31 }, (_, i) => new File(['x'], `f${i}.txt`)));

        expect(el.querySelector('.file-errors')!.textContent).toContain(
          'A credit can have at most 30 files.',
        );
        expect(button('Save').disabled).toBe(true);
      });

      it('should send an empty file list when nothing is attached', async () => {
        await fillValid();
        await calculate();
        button('Save').click();
        await fixture.whenStable();
        expect(creditService.createWithFiles.mock.calls[0][1]).toEqual([]);
      });
    });

    describe('collateral', () => {
      /**
       * Ticks or unticks the collateral checkbox.
       */
      async function toggleCollateral(): Promise<void> {
        control('hasCollateral')!.querySelector<HTMLInputElement>('input')!.click();
        await fixture.whenStable();
      }

      it('should hide the collateral fields until the box is checked', async () => {
        expect(control('collateralValue')).toBeNull();
        expect(control('collateralDescription')).toBeNull();
        await toggleCollateral();
        expect(control('collateralValue')).not.toBeNull();
        expect(control('collateralDescription')).not.toBeNull();
      });

      it('should require value and description when checked', async () => {
        await toggleCollateral();
        await touch('collateralValue');
        await touch('collateralDescription');
        expect(errors('collateralValue')).toEqual(['Collateral value is required.']);
        expect(errors('collateralDescription')).toEqual(['Collateral description is required.']);
        await type('collateralDescription', '   ');
        expect(errors('collateralDescription')).toEqual(['Collateral description is required.']);
      });

      it('should limit the description to 500 characters and the value to 2 decimals', async () => {
        await toggleCollateral();
        expect(control('collateralDescription')!.getAttribute('maxlength')).toBe('500');
        await type('collateralValue', '0');
        await touch('collateralValue');
        expect(errors('collateralValue')).toEqual(['Collateral value must be greater than zero.']);
        await type('collateralValue', '5.123');
        expect(errors('collateralValue')).toEqual([
          'Collateral value may have at most 2 decimals.',
        ]);
      });

      it('should send the collateral when checked', async () => {
        await fillValid();
        await toggleCollateral();
        await type('collateralValue', '20000');
        await type('collateralDescription', 'A flat in Sofia');
        await calculate();

        button('Save').click();
        await fixture.whenStable();

        expect(creditService.createWithFiles).toHaveBeenCalledWith(
          expect.objectContaining({
            hasCollateral: true,
            collateralValue: 20000,
            collateralDescription: 'A flat in Sofia',
          }),
          [],
        );
      });

      it('should hide and clear the collateral fields when unchecked', async () => {
        await fillValid();
        await toggleCollateral();
        await type('collateralValue', '20000');
        await type('collateralDescription', 'A flat in Sofia');

        await toggleCollateral();
        expect(control('collateralValue')).toBeNull();
        expect(control('collateralDescription')).toBeNull();

        await toggleCollateral();
        expect(control('collateralValue')!.value).toBe('');
        expect(control('collateralDescription')!.value).toBe('');

        await toggleCollateral();
        await calculate();
        button('Save').click();
        await fixture.whenStable();
        expect(creditService.createWithFiles).toHaveBeenCalledWith(
          expect.objectContaining({
            hasCollateral: false,
            collateralValue: null,
            collateralDescription: null,
          }),
          [],
        );
      });
    });
  });

  describe('edit credit', () => {
    it('should not offer a file picker', async () => {
      await render('9');
      expect(el.querySelector('app-pending-files')).toBeNull();
    });

    it('should load the credit by the route id and prefill the form with its saved plan', async () => {
      await render('9');
      expect(creditService.get).toHaveBeenCalledWith(9);
      expect(el.querySelector('mat-card-title')?.textContent).toContain(
        'Edit credit CR-2026-000009',
      );
      expect(control('clientId')!.value).toBe('Acme (123)');
      expect(control('amount')!.value).toBe('10000');
      expect(control('interestRate')!.value).toBe('5.25');
      expect(control('startDate')!.value).toBe('15.01.2026');
      expect(control('endDate')!.value).toBe('15.01.2027');
      expect(el.querySelector('app-payment-plan')).not.toBeNull();
      expect(button('Save').disabled).toBe(false);
      expect(button('Calculate').disabled).toBe(false);
      expect(cancelHref()).toBe('/credits/9');
    });

    it('should prefill and show the collateral of a secured credit', async () => {
      await render('9', undefined, {
        ...saved,
        hasCollateral: true,
        collateralValue: 20000,
        collateralDescription: 'A flat',
      });
      expect(control('collateralValue')!.value).toBe('20000');
      expect(control('collateralDescription')!.value).toBe('A flat');
    });

    it('should keep the saved plan after the prefill settles (autocomplete, datepickers)', async () => {
      await render('9');
      await fixture.whenStable();
      expect(el.querySelector('app-payment-plan')).not.toBeNull();
      expect(button('Save').disabled).toBe(false);
    });

    it('should clear the plan and disable Save when an input changes, and Calculate re-enables Save', async () => {
      await render('9');

      await type('amount', '12000');
      expect(el.querySelector('app-payment-plan')).toBeNull();
      expect(button('Save').disabled).toBe(true);

      await calculate();
      expect(creditService.calculate).toHaveBeenCalledWith(
        expect.objectContaining({ amount: 12000, startDate: '2026-01-15' }),
      );
      expect(el.querySelector('app-payment-plan')).not.toBeNull();
      expect(button('Save').disabled).toBe(false);
    });

    it('should save with PUT and the inputs only, without changing anything', async () => {
      await render('9');

      button('Save').click();
      await fixture.whenStable();

      expect(creditService.update).toHaveBeenCalledWith(9, {
        clientId: 3,
        type: 'MORTGAGE',
        amount: 10000,
        currency: 'EUR',
        interestRate: 5.25,
        startDate: '2026-01-15',
        endDate: '2027-01-15',
        hasCollateral: false,
        collateralValue: null,
        collateralDescription: null,
      });
      expect(creditService.createWithFiles).not.toHaveBeenCalled();
      expect(notification.success).toHaveBeenCalledWith('Credit CR-2026-000009 was updated.');
      expect(navigate).toHaveBeenCalledWith(['/credits', 9]);
    });

    it('should show "Credit not found" when the backend answers 404', async () => {
      await render('9', new HttpErrorResponse({ status: 404 }));
      expect(el.querySelector('mat-card-title')?.textContent).toContain('Credit not found');
      expect(el.querySelector('form')).toBeNull();
    });

    it('should show "Credit not found" for an id that is not a number, without calling the backend', async () => {
      await render('abc');
      expect(creditService.get).not.toHaveBeenCalled();
      expect(el.querySelector('mat-card-title')?.textContent).toContain('Credit not found');
    });
  });
});
