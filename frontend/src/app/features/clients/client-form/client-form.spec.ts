import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';
import { Client } from '../client.model';
import { ClientService } from '../client.service';
import { ClientForm, ClientFormData } from './client-form';

describe('ClientForm', () => {
  let fixture: ComponentFixture<ClientForm>;
  let el: HTMLElement;
  let dialogRef: { close: ReturnType<typeof vi.fn> };
  let clientService: { create: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn> };

  const existing: Client = {
    id: 7,
    name: 'Acme',
    identifier: '123',
    address: 'Sofia',
    description: 'note',
    archived: false,
    archivedAt: null,
    createdAt: '2026-10-27T10:00:00Z',
    updatedAt: '2026-10-27T10:00:00Z',
  };

  /**
   * Renders the form dialog content.
   * @param data the dialog data (with or without a client to edit)
   */
  async function render(data: ClientFormData): Promise<void> {
    dialogRef = { close: vi.fn() };
    clientService = { create: vi.fn(), update: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ClientForm],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: ClientService, useValue: clientService },
      ],
    });
    fixture = TestBed.createComponent(ClientForm);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  }

  /**
   * Types a value into a field and lets the form validate it.
   * @param name the control name
   * @param value the text to enter
   */
  async function type(name: string, value: string): Promise<void> {
    const input = el.querySelector<HTMLInputElement | HTMLTextAreaElement>(`[name="${name}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  }

  /**
   * Fills every required field with valid values.
   */
  async function fillValid(): Promise<void> {
    await type('name', 'New Ltd');
    await type('identifier', '999');
    await type('address', 'Plovdiv');
  }

  /**
   * Finds the submit button.
   * @returns the submit button
   */
  function submitButton(): HTMLButtonElement {
    return el.querySelector<HTMLButtonElement>('button[type="submit"]')!;
  }

  /**
   * Submits the form and waits for the view to update.
   */
  async function submit(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  /**
   * Collects the texts of all visible error messages.
   * @returns the trimmed `mat-error` texts
   */
  function errors(): string[] {
    return Array.from(el.querySelectorAll('mat-error')).map((e) => e.textContent!.trim());
  }

  describe('creating', () => {
    beforeEach(() => render({}));

    it('should show the "New client" title, empty fields and a disabled Create button', () => {
      expect(el.querySelector('h2')?.textContent).toContain('New client');
      expect(el.querySelector<HTMLInputElement>('[name="name"]')!.value).toBe('');
      expect(submitButton().textContent).toContain('Create');
      expect(submitButton().disabled).toBe(true);
    });

    it('should show a required message for a touched empty field', async () => {
      await type('name', '');
      expect(errors()).toEqual(['Name is required.']);
    });

    it('should treat whitespace-only text as missing', async () => {
      await fillValid();
      await type('address', '   ');
      expect(errors()).toEqual(['Address is required.']);
      expect(submitButton().disabled).toBe(true);
    });

    it('should limit every field to the backend maximum length', () => {
      const max = (name: string) => el.querySelector(`[name="${name}"]`)!.getAttribute('maxlength');
      expect([max('name'), max('identifier'), max('address'), max('description')]).toEqual([
        '100',
        '50',
        '200',
        '500',
      ]);
    });

    it('should enable Create once the required fields are filled', async () => {
      await fillValid();
      expect(submitButton().disabled).toBe(false);
    });

    it('should create the client, sending an empty description as null, and close with it', async () => {
      const created = { ...existing, id: 8, name: 'New Ltd' };
      clientService.create.mockReturnValue(of(created));
      await fillValid();

      await submit();

      expect(clientService.create).toHaveBeenCalledWith({
        name: 'New Ltd',
        identifier: '999',
        address: 'Plovdiv',
        description: null,
      });
      expect(dialogRef.close).toHaveBeenCalledWith(created);
    });

    it('should disable the buttons while saving', async () => {
      clientService.create.mockReturnValue(new Subject<Client>());
      await fillValid();

      await submit();

      expect(submitButton().disabled).toBe(true);
      expect(clientService.create).toHaveBeenCalledTimes(1);
    });

    it('should show the backend field errors of a 400 on the matching fields and stay open', async () => {
      clientService.create.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 400,
              error: {
                detail: 'Validation failed.',
                errors: { identifier: 'Identifier is invalid.' },
              },
            }),
        ),
      );
      await fillValid();

      await submit();

      expect(errors()).toEqual(['Identifier is invalid.']);
      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(submitButton().disabled).toBe(true);
    });

    it('should stay open and re-enable saving after another error', async () => {
      clientService.create.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Boom.' } })),
      );
      await fillValid();

      await submit();

      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(errors()).toEqual([]);
      expect(submitButton().disabled).toBe(false);
    });
  });

  describe('editing', () => {
    beforeEach(() => render({ client: existing }));

    it('should show the "Edit client" title and the current values', () => {
      expect(el.querySelector('h2')?.textContent).toContain('Edit client');
      expect(el.querySelector<HTMLInputElement>('[name="name"]')!.value).toBe('Acme');
      expect(el.querySelector<HTMLTextAreaElement>('[name="description"]')!.value).toBe('note');
      expect(submitButton().textContent).toContain('Save');
    });

    it('should update the client by id and close with the result', async () => {
      const updated = { ...existing, name: 'Acme Ltd' };
      clientService.update.mockReturnValue(of(updated));
      await type('name', 'Acme Ltd');

      await submit();

      expect(clientService.update).toHaveBeenCalledWith(7, {
        name: 'Acme Ltd',
        identifier: '123',
        address: 'Sofia',
        description: 'note',
      });
      expect(clientService.create).not.toHaveBeenCalled();
      expect(dialogRef.close).toHaveBeenCalledWith(updated);
    });
  });
});
