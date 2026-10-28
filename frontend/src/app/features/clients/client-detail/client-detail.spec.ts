import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';
import { NotificationService } from '../../../shared/notification/notification.service';
import { ClientForm } from '../client-form/client-form';
import { Client } from '../client.model';
import { ClientService } from '../client.service';
import { ClientDetail } from './client-detail';

describe('ClientDetail', () => {
  let fixture: ComponentFixture<ClientDetail>;
  let el: HTMLElement;
  let clientService: {
    get: ReturnType<typeof vi.fn>;
    archive: ReturnType<typeof vi.fn>;
    restore: ReturnType<typeof vi.fn>;
  };
  let dialog: { open: ReturnType<typeof vi.fn> };
  let notification: { success: ReturnType<typeof vi.fn> };
  let navigate: ReturnType<typeof vi.spyOn>;

  const active: Client = {
    id: 7,
    name: 'Acme',
    identifier: '123',
    address: 'Sofia',
    description: 'Good payer',
    archived: false,
    archivedAt: null,
    createdAt: '2026-10-27T10:00:00Z',
    updatedAt: '2026-10-27T11:00:00Z',
  };
  const archived: Client = { ...active, archived: true, archivedAt: '2026-10-27T12:00:00Z' };

  /**
   * Renders the page for the given route id, backend answer and role.
   * @param options route id, what `get` returns, and whether the user is an ADMIN
   */
  async function render(options: {
    id?: string;
    client?: Client;
    error?: HttpErrorResponse;
    admin?: boolean;
  }): Promise<void> {
    clientService = {
      get: vi
        .fn()
        .mockReturnValue(
          options.error ? throwError(() => options.error) : of(options.client ?? active),
        ),
      archive: vi.fn(),
      restore: vi.fn(),
    };
    dialog = { open: vi.fn() };
    notification = { success: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ClientDetail],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: options.id ?? '7' }) } },
        },
        { provide: ClientService, useValue: clientService },
        { provide: MatDialog, useValue: dialog },
        { provide: NotificationService, useValue: notification },
        { provide: AuthService, useValue: { isAdmin: () => options.admin ?? false } },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(ClientDetail);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  }

  /**
   * Finds a button by its visible label.
   * @param label the button text (without the icon)
   * @returns the button, or undefined if not shown
   */
  function button(label: string): HTMLButtonElement | undefined {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent!.includes(label));
  }

  it('should load the client from the route id and show its fields', async () => {
    await render({});
    expect(clientService.get).toHaveBeenCalledWith(7);
    expect(el.querySelector('mat-card-title')?.textContent).toContain('Acme');
    const text = el.querySelector('dl')!.textContent!;
    expect(text).toContain('123');
    expect(text).toContain('Sofia');
    expect(text).toContain('Good payer');
  });

  it('should show a dash when there is no description', async () => {
    await render({ client: { ...active, description: null } });
    expect(el.querySelector('.description')?.textContent?.trim()).toBe('—');
  });

  it('should offer a link back to the list', async () => {
    await render({});
    expect(el.querySelector('a[href="/clients"]')?.textContent).toContain('Back to list');
  });

  it('should not show Edit, Archive or Restore to a plain USER', async () => {
    await render({});
    expect(button('Edit')).toBeUndefined();
    expect(button('Archive')).toBeUndefined();
    expect(button('Restore')).toBeUndefined();
  });

  it('should show Edit and Archive to an ADMIN for an active client', async () => {
    await render({ admin: true });
    expect(button('Edit')).toBeDefined();
    expect(button('Archive')).toBeDefined();
    expect(button('Restore')).toBeUndefined();
  });

  it('should mark an archived client and offer Restore instead of Archive to an ADMIN', async () => {
    await render({ admin: true, client: archived });
    expect(el.querySelector('mat-card-title')?.textContent).toContain('Archived');
    expect(button('Restore')).toBeDefined();
    expect(button('Archive')).toBeUndefined();
  });

  it('should show "Client not found" when the backend answers 404', async () => {
    await render({ error: new HttpErrorResponse({ status: 404 }) });
    expect(el.querySelector('mat-card-title')?.textContent).toContain('Client not found');
  });

  it('should show "Client not found" for an id that is not a number, without calling the backend', async () => {
    await render({ id: 'abc' });
    expect(clientService.get).not.toHaveBeenCalled();
    expect(el.querySelector('mat-card-title')?.textContent).toContain('Client not found');
  });

  describe('as ADMIN', () => {
    it('should open the edit dialog with the client', async () => {
      await render({ admin: true });
      dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

      button('Edit')!.click();

      expect(dialog.open).toHaveBeenCalledWith(ClientForm, { data: { client: active } });
      expect(navigate).not.toHaveBeenCalled();
    });

    it('should go back to the list with a message after a successful edit', async () => {
      await render({ admin: true });
      dialog.open.mockReturnValue({ afterClosed: () => of({ ...active, name: 'Acme Ltd' }) });

      button('Edit')!.click();

      expect(notification.success).toHaveBeenCalledWith('Client "Acme Ltd" was updated.');
      expect(navigate).toHaveBeenCalledWith(['/clients']);
    });

    it('should ask for confirmation before archiving', async () => {
      await render({ admin: true });
      dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

      button('Archive')!.click();

      expect(dialog.open).toHaveBeenCalledWith(
        ConfirmDialog,
        expect.objectContaining({
          data: expect.objectContaining({ title: 'Archive client?', confirmLabel: 'Archive' }),
        }),
      );
      expect(clientService.archive).not.toHaveBeenCalled();
    });

    it('should archive after confirmation and go back to the list with a message', async () => {
      await render({ admin: true });
      dialog.open.mockReturnValue({ afterClosed: () => of(true) });
      clientService.archive.mockReturnValue(of(undefined));

      button('Archive')!.click();

      expect(clientService.archive).toHaveBeenCalledWith(7);
      expect(notification.success).toHaveBeenCalledWith('Client "Acme" was archived.');
      expect(navigate).toHaveBeenCalledWith(['/clients']);
    });

    it('should stay on the page when archiving is refused (e.g. 409)', async () => {
      await render({ admin: true });
      dialog.open.mockReturnValue({ afterClosed: () => of(true) });
      clientService.archive.mockReturnValue(
        throwError(
          () => new HttpErrorResponse({ status: 409, error: { detail: 'Has an active credit.' } }),
        ),
      );

      button('Archive')!.click();
      await fixture.whenStable();

      expect(notification.success).not.toHaveBeenCalled();
      expect(navigate).not.toHaveBeenCalled();
      expect(button('Archive')!.disabled).toBe(false);
    });

    it('should restore an archived client and go back to the list with a message', async () => {
      await render({ admin: true, client: archived });
      clientService.restore.mockReturnValue(of(active));

      button('Restore')!.click();

      expect(clientService.restore).toHaveBeenCalledWith(7);
      expect(notification.success).toHaveBeenCalledWith('Client "Acme" was restored.');
      expect(navigate).toHaveBeenCalledWith(['/clients']);
    });
  });
});
