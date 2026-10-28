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
  let clientService: {
    createWithFiles: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
  };

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
    clientService = { createWithFiles: vi.fn(), update: vi.fn() };
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

    /**
     * Simulates choosing files in the picker.
     * @param files the files to choose
     */
    async function pick(files: File[]): Promise<void> {
      const input = el.querySelector<HTMLInputElement>('input[type="file"]')!;
      Object.defineProperty(input, 'files', { value: files, configurable: true });
      input.dispatchEvent(new Event('change'));
      await fixture.whenStable();
    }

    it('should offer an optional file picker restricted to the allowed types', () => {
      const input = el.querySelector<HTMLInputElement>('input[type="file"]')!;
      expect(input.multiple).toBe(true);
      expect(input.accept).toContain('.pdf');
      expect(input.accept).toContain('image/heic');
    });

    it('should send the chosen files with the create request', async () => {
      clientService.createWithFiles.mockReturnValue(of(existing));
      const pdf = new File(['x'], 'a.pdf', { type: 'application/pdf' });
      await fillValid();
      await pick([pdf]);
      expect(el.textContent).toContain('a.pdf');

      await submit();

      expect(clientService.createWithFiles.mock.calls[0][1]).toEqual([pdf]);
    });

    it('should block Create and explain when a file has a disallowed type', async () => {
      await fillValid();
      await pick([new File(['x'], 'run.exe')]);
      expect(el.querySelector('.file-errors')?.textContent).toContain('type that is not allowed');
      expect(submitButton().disabled).toBe(true);
      await submit();
      expect(clientService.createWithFiles).not.toHaveBeenCalled();
    });

    it('should block Create for a file over 5 MB and unblock after removing it', async () => {
      await fillValid();
      const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.pdf');
      await pick([big]);
      expect(el.querySelector('.file-errors')?.textContent).toContain('larger than 5 MB');
      expect(submitButton().disabled).toBe(true);

      el.querySelector<HTMLButtonElement>('button[aria-label="Remove big.pdf"]')!.click();
      await fixture.whenStable();

      expect(el.querySelector('.file-errors')).toBeNull();
      expect(submitButton().disabled).toBe(false);
    });

    it('should block Create for duplicate file names', async () => {
      await fillValid();
      await pick([new File(['x'], 'a.pdf'), new File(['y'], 'a.pdf')]);
      expect(el.querySelector('.file-errors')?.textContent).toContain('already attached');
      expect(submitButton().disabled).toBe(true);
    });

    it('should block Create above 30 files', async () => {
      await fillValid();
      await pick(Array.from({ length: 31 }, (_, i) => new File(['x'], `f${i}.txt`)));
      expect(el.querySelector('.file-errors')?.textContent).toContain('at most 30 files');
      expect(submitButton().disabled).toBe(true);
    });

    it('should enable Create once the required fields are filled', async () => {
      await fillValid();
      expect(submitButton().disabled).toBe(false);
    });

    it('should create the client, sending an empty description as null, and close with it', async () => {
      const created = { ...existing, id: 8, name: 'New Ltd' };
      clientService.createWithFiles.mockReturnValue(of(created));
      await fillValid();

      await submit();

      expect(clientService.createWithFiles).toHaveBeenCalledWith(
        { name: 'New Ltd', identifier: '999', address: 'Plovdiv', description: null },
        [],
      );
      expect(dialogRef.close).toHaveBeenCalledWith(created);
    });

    it('should disable the buttons while saving', async () => {
      clientService.createWithFiles.mockReturnValue(new Subject<Client>());
      await fillValid();

      await submit();

      expect(submitButton().disabled).toBe(true);
      expect(clientService.createWithFiles).toHaveBeenCalledTimes(1);
    });

    it('should show the backend field errors of a 400 on the matching fields and stay open', async () => {
      clientService.createWithFiles.mockReturnValue(
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
      clientService.createWithFiles.mockReturnValue(
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

    it('should not offer a file picker', () => {
      expect(el.querySelector('input[type="file"]')).toBeNull();
    });

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
      expect(clientService.createWithFiles).not.toHaveBeenCalled();
      expect(dialogRef.close).toHaveBeenCalledWith(updated);
    });
  });
});
