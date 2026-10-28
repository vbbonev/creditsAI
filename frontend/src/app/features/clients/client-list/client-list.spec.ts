import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { Page } from '../../../shared/models/page';
import { NotificationService } from '../../../shared/notification/notification.service';
import { ClientForm } from '../client-form/client-form';
import { Client, ClientQuery } from '../client.model';
import { ClientService } from '../client.service';
import { ClientList, SEARCH_DEBOUNCE_MS } from './client-list';

describe('ClientList', () => {
  let fixture: ComponentFixture<ClientList>;
  let el: HTMLElement;
  let clientService: { list: ReturnType<typeof vi.fn> };
  let dialog: { open: ReturnType<typeof vi.fn> };
  let notification: { success: ReturnType<typeof vi.fn> };

  /**
   * Builds a client row.
   * @param id the client id
   * @param name the client name
   * @param archived whether the client is archived
   * @returns the client
   */
  function client(id: number, name: string, archived = false): Client {
    return {
      id,
      name,
      identifier: `ID-${id}`,
      address: `Street ${id}`,
      description: null,
      archived,
      archivedAt: archived ? '2026-10-27T10:00:00Z' : null,
      createdAt: '2026-10-27T10:00:00Z',
      updatedAt: '2026-10-27T10:00:00Z',
    };
  }

  /**
   * Wraps rows into a Spring page.
   * @param content the rows
   * @param totalElements the total over all pages
   * @returns the page
   */
  function page(content: Client[], totalElements = content.length): Page<Client> {
    return { content, page: { size: 10, number: 0, totalElements, totalPages: 1 } };
  }

  /**
   * Renders the list for a user with the given role.
   * @param admin whether the user is an ADMIN
   * @param firstPage what the backend returns for every list request
   */
  async function render(
    admin: boolean,
    firstPage = page([client(1, 'Acme'), client(2, 'Beta')]),
  ): Promise<void> {
    clientService = { list: vi.fn().mockReturnValue(of(firstPage)) };
    dialog = { open: vi.fn() };
    notification = { success: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ClientList],
      providers: [
        provideRouter([]),
        { provide: ClientService, useValue: clientService },
        { provide: MatDialog, useValue: dialog },
        { provide: NotificationService, useValue: notification },
        { provide: AuthService, useValue: { isAdmin: () => admin } },
      ],
    });
    fixture = TestBed.createComponent(ClientList);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  }

  /**
   * Returns the query of the most recent list request.
   * @returns the query passed to `ClientService.list`
   */
  function lastQuery(): ClientQuery {
    return clientService.list.mock.lastCall![0] as ClientQuery;
  }

  /**
   * Reads the visible table rows.
   * @returns the cell texts of each data row
   */
  function rows(): string[][] {
    return Array.from(el.querySelectorAll('tr.client-row')).map((tr) =>
      Array.from(tr.querySelectorAll('td')).map((td) => td.textContent!.trim()),
    );
  }

  afterEach(() => vi.useRealTimers());

  it('should load the first page sorted by name with 10 rows per page', async () => {
    await render(false);
    expect(clientService.list).toHaveBeenCalledTimes(1);
    expect(lastQuery()).toEqual({
      search: '',
      includeArchived: false,
      page: 0,
      size: 10,
      sort: 'name',
      direction: 'asc',
    });
  });

  it('should show name, identifier and address of each client', async () => {
    await render(false);
    expect(Array.from(el.querySelectorAll('th')).map((th) => th.textContent!.trim())).toEqual([
      'Name',
      'Identifier',
      'Address',
    ]);
    expect(rows()).toEqual([
      ['Acme', 'ID-1', 'Street 1'],
      ['Beta', 'ID-2', 'Street 2'],
    ]);
  });

  it('should show the total in the paginator', async () => {
    await render(false, page([client(1, 'Acme')], 25));
    expect(el.querySelector('.mat-mdc-paginator-range-label')?.textContent).toContain('of 25');
  });

  it('should show a message when there are no clients', async () => {
    await render(false, page([]));
    expect(el.querySelector('.no-data')?.textContent).toContain('No clients yet.');
  });

  it('should search after the user stops typing, from the first page', async () => {
    await render(false);
    vi.useFakeTimers();
    const input = el.querySelector<HTMLInputElement>('input[name="search"]')!;

    for (const text of ['a', 'ac', ' acme ']) {
      input.value = text;
      input.dispatchEvent(new Event('input'));
    }
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS - 1);
    expect(clientService.list).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(1);
    expect(clientService.list).toHaveBeenCalledTimes(2);
    expect(lastQuery()).toMatchObject({ search: 'acme', page: 0 });
  });

  it('should say that nothing matches when a search finds no clients', async () => {
    await render(false);
    clientService.list.mockReturnValue(of(page([])));
    vi.useFakeTimers();
    const input = el.querySelector<HTMLInputElement>('input[name="search"]')!;
    input.value = 'zzz';
    input.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    vi.useRealTimers();
    await fixture.whenStable();

    expect(el.querySelector('.no-data')?.textContent).toContain('No clients match your search.');
  });

  it('should not show the archived toggle to a plain USER', async () => {
    await render(false);
    expect(el.querySelector('mat-slide-toggle')).toBeNull();
  });

  it('should list active and archived clients when an ADMIN turns the toggle on', async () => {
    await render(true);
    const toggle = el.querySelector('mat-slide-toggle')!;
    expect(toggle.textContent).toContain('Show archived clients');

    toggle.querySelector('button')!.click();
    await fixture.whenStable();

    expect(lastQuery()).toMatchObject({ includeArchived: true, page: 0 });
  });

  it('should mark archived clients in the list', async () => {
    await render(true, page([client(1, 'Acme'), client(2, 'Old', true)]));
    const archivedRows = el.querySelectorAll('tr.client-row.archived');
    expect(archivedRows.length).toBe(1);
    expect(archivedRows[0].textContent).toContain('Archived');
  });

  it('should sort by the clicked column from the first page', async () => {
    await render(false);
    const header = Array.from(el.querySelectorAll<HTMLElement>('th')).find(
      (th) => th.textContent!.trim() === 'Identifier',
    )!;

    header.click();
    await fixture.whenStable();

    expect(lastQuery()).toMatchObject({ sort: 'identifier', direction: 'asc', page: 0 });
  });

  it('should not offer sorting by address', async () => {
    await render(false);
    const sortable = Array.from(el.querySelectorAll('th[mat-sort-header]')).map((th) =>
      th.textContent!.trim(),
    );
    expect(sortable).toEqual(['Name', 'Identifier']);
  });

  it('should load the next page from the paginator', async () => {
    await render(false, page([client(1, 'Acme')], 25));

    el.querySelector<HTMLButtonElement>('.mat-mdc-paginator-navigation-next')!.click();
    await fixture.whenStable();

    expect(lastQuery()).toMatchObject({ page: 1, size: 10 });
  });

  it('should open the detail page when a row is clicked', async () => {
    await render(false);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    el.querySelector<HTMLElement>('tr.client-row')!.click();

    expect(navigate).toHaveBeenCalledWith(['/clients', 1]);
  });

  it('should open the form dialog for a new client', async () => {
    await render(false);
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    el.querySelector<HTMLButtonElement>('mat-card-header button')!.click();

    expect(dialog.open).toHaveBeenCalledWith(ClientForm, { data: {} });
  });

  it('should confirm and reload after a client was created', async () => {
    await render(false);
    dialog.open.mockReturnValue({ afterClosed: () => of(client(3, 'Gamma')) });

    el.querySelector<HTMLButtonElement>('mat-card-header button')!.click();
    await fixture.whenStable();

    expect(notification.success).toHaveBeenCalledWith('Client "Gamma" was created.');
    expect(clientService.list).toHaveBeenCalledTimes(2);
  });

  it('should do nothing when the new-client dialog is cancelled', async () => {
    await render(false);
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    el.querySelector<HTMLButtonElement>('mat-card-header button')!.click();

    expect(notification.success).not.toHaveBeenCalled();
    expect(clientService.list).toHaveBeenCalledTimes(1);
  });
});
