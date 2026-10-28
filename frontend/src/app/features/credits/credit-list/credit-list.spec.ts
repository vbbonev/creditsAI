import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { Page } from '../../../shared/models/page';
import { CreditQuery, CreditSummary } from '../credit.model';
import { CreditService } from '../credit.service';
import { CreditList, SEARCH_DEBOUNCE_MS } from './credit-list';

describe('CreditList', () => {
  let fixture: ComponentFixture<CreditList>;
  let el: HTMLElement;
  let creditService: { list: ReturnType<typeof vi.fn> };

  /**
   * Builds a credit row.
   * @param id the credit id
   * @param archived whether the credit is archived
   * @returns the credit
   */
  function credit(id: number, archived = false): CreditSummary {
    return {
      id,
      creditNumber: `CR-2026-00000${id}`,
      client: { id: 1, name: 'Acme', identifier: '123' },
      type: 'MORTGAGE',
      amount: 10000,
      currency: 'EUR',
      interestRate: 5.25,
      startDate: '2026-01-05',
      endDate: '2027-01-05',
      termMonths: 12,
      monthlyPayment: 856.07,
      status: 'ACTIVE',
      archived,
      createdAt: '2026-10-27T10:00:00Z',
    };
  }

  /**
   * Wraps rows into a Spring page.
   * @param content the rows
   * @param totalElements the total over all pages
   * @returns the page
   */
  function page(content: CreditSummary[], totalElements = content.length): Page<CreditSummary> {
    return { content, page: { size: 10, number: 0, totalElements, totalPages: 1 } };
  }

  /**
   * Renders the list for a user with the given role.
   * @param admin whether the user is an ADMIN
   * @param firstPage what the backend returns for every list request
   */
  async function render(admin: boolean, firstPage = page([credit(1), credit(2)])): Promise<void> {
    creditService = { list: vi.fn().mockReturnValue(of(firstPage)) };
    TestBed.configureTestingModule({
      imports: [CreditList],
      providers: [
        provideRouter([]),
        { provide: CreditService, useValue: creditService },
        { provide: AuthService, useValue: { isAdmin: () => admin } },
      ],
    });
    fixture = TestBed.createComponent(CreditList);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  }

  /**
   * Returns the query of the most recent list request.
   * @returns the query passed to `CreditService.list`
   */
  function lastQuery(): CreditQuery {
    return creditService.list.mock.lastCall![0] as CreditQuery;
  }

  afterEach(() => vi.useRealTimers());

  it('should load the first page sorted by credit number with 10 rows per page', async () => {
    await render(false);
    expect(creditService.list).toHaveBeenCalledTimes(1);
    expect(lastQuery()).toEqual({
      search: '',
      status: null,
      includeArchived: false,
      page: 0,
      size: 10,
      sort: 'creditNumber',
      direction: 'asc',
    });
  });

  it('should show all columns of a credit with formatted money, dates and rate', async () => {
    await render(false);
    expect(Array.from(el.querySelectorAll('th')).map((th) => th.textContent!.trim())).toEqual([
      'Credit number',
      'Client',
      'Type',
      'Amount',
      'Rate',
      'Start date',
      'End date',
      'Monthly payment',
      'Status',
    ]);
    const cells = Array.from(el.querySelector('tr.credit-row')!.querySelectorAll('td')).map((td) =>
      td.textContent!.replace(/\s+/g, ' ').trim(),
    );
    expect(cells).toEqual([
      'CR-2026-000001',
      'Acme (123)',
      'Mortgage',
      '€10,000.00',
      '5.25%',
      '05.01.2026',
      '05.01.2027',
      '€856.07',
      'Active',
    ]);
  });

  it('should show the total in the paginator', async () => {
    await render(false, page([credit(1)], 25));
    expect(el.querySelector('.mat-mdc-paginator-range-label')?.textContent).toContain('of 25');
  });

  it('should show a message when there are no credits', async () => {
    await render(false, page([]));
    expect(el.querySelector('.no-data')?.textContent).toContain('No credits yet.');
  });

  it('should search after the user stops typing, from the first page', async () => {
    await render(false);
    vi.useFakeTimers();
    const input = el.querySelector<HTMLInputElement>('input[name="search"]')!;

    for (const text of ['c', 'cr', ' CR-2026 ']) {
      input.value = text;
      input.dispatchEvent(new Event('input'));
    }
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS - 1);
    expect(creditService.list).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(1);
    expect(creditService.list).toHaveBeenCalledTimes(2);
    expect(lastQuery()).toMatchObject({ search: 'CR-2026', page: 0 });
  });

  it('should filter by status from the first page and back to all', async () => {
    await render(false, page([credit(1)], 25));
    el.querySelector<HTMLButtonElement>('.mat-mdc-paginator-navigation-next')!.click();
    await fixture.whenStable();
    expect(lastQuery().page).toBe(1);

    el.querySelector<HTMLElement>('mat-select')!.click();
    await fixture.whenStable();
    const options = Array.from(document.querySelectorAll<HTMLElement>('mat-option'));
    expect(options.map((o) => o.textContent!.trim())).toEqual(['All', 'Active', 'Inactive']);

    options[2].click();
    await fixture.whenStable();
    expect(lastQuery()).toMatchObject({ status: 'INACTIVE', page: 0 });

    el.querySelector<HTMLElement>('mat-select')!.click();
    await fixture.whenStable();
    document.querySelectorAll<HTMLElement>('mat-option')[0].click();
    await fixture.whenStable();
    expect(lastQuery().status).toBeNull();
  });

  it('should not show the archived toggle to a plain USER', async () => {
    await render(false);
    expect(el.querySelector('mat-slide-toggle')).toBeNull();
  });

  it('should list active and archived credits when an ADMIN turns the toggle on', async () => {
    await render(true);
    const toggle = el.querySelector('mat-slide-toggle')!;
    expect(toggle.textContent).toContain('Show archived credits');

    toggle.querySelector('button')!.click();
    await fixture.whenStable();

    expect(lastQuery()).toMatchObject({ includeArchived: true, page: 0 });
  });

  it('should mark archived credits in the list', async () => {
    await render(true, page([credit(1), credit(2, true)]));
    const archivedRows = el.querySelectorAll('tr.credit-row.archived');
    expect(archivedRows.length).toBe(1);
    expect(archivedRows[0].textContent).toContain('Archived');
  });

  it('should sort by the clicked column from the first page', async () => {
    await render(false);
    const header = Array.from(el.querySelectorAll<HTMLElement>('th')).find(
      (th) => th.textContent!.trim() === 'Start date',
    )!;

    header.click();
    await fixture.whenStable();

    expect(lastQuery()).toMatchObject({ sort: 'startDate', direction: 'asc', page: 0 });
  });

  it('should only offer sorting by the fields the backend allows', async () => {
    await render(false);
    const sortable = Array.from(el.querySelectorAll('th[mat-sort-header]')).map((th) =>
      th.textContent!.trim(),
    );
    expect(sortable).toEqual(['Credit number', 'Amount', 'Start date', 'End date']);
  });

  it('should load the next page from the paginator', async () => {
    await render(false, page([credit(1)], 25));

    el.querySelector<HTMLButtonElement>('.mat-mdc-paginator-navigation-next')!.click();
    await fixture.whenStable();

    expect(lastQuery()).toMatchObject({ page: 1, size: 10 });
  });

  it('should open the detail page when a row is clicked', async () => {
    await render(false);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    el.querySelector<HTMLElement>('tr.credit-row')!.click();

    expect(navigate).toHaveBeenCalledWith(['/credits', 1]);
  });

  it('should link "New credit" to the new credit page', async () => {
    await render(false);
    const link = el.querySelector('mat-card-header a')!;
    expect(link.getAttribute('href')).toBe('/credits/new');
    expect(link.textContent).toContain('New credit');
  });
});
