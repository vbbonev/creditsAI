import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';
import { NotificationService } from '../../../shared/notification/notification.service';
import { AttachmentPreviewDialog } from '../attachment-preview-dialog/attachment-preview-dialog';
import { Attachment } from '../attachment.model';
import { ClientService } from '../client.service';
import { ClientAttachments } from './client-attachments';

describe('ClientAttachments', () => {
  let fixture: ComponentFixture<ClientAttachments>;
  let el: HTMLElement;
  let service: {
    listAttachments: ReturnType<typeof vi.fn>;
    downloadAttachment: ReturnType<typeof vi.fn>;
    uploadAttachments: ReturnType<typeof vi.fn>;
    deleteAttachment: ReturnType<typeof vi.fn>;
  };
  let dialog: { open: ReturnType<typeof vi.fn> };
  let notification: { success: ReturnType<typeof vi.fn> };
  let createUrl: ReturnType<typeof vi.fn>;
  let revokeUrl: ReturnType<typeof vi.fn>;
  let urlCounter: number;

  const pdf: Attachment = {
    id: 1,
    fileName: 'contract.pdf',
    contentType: 'application/pdf',
    sizeBytes: 2048,
    uploadedBy: 'user1',
    uploadedAt: '2026-10-27T10:00:00Z',
  };
  const png: Attachment = { ...pdf, id: 2, fileName: 'photo.png', contentType: 'image/png' };
  const heic: Attachment = { ...pdf, id: 3, fileName: 'scan.heic', contentType: 'image/heic' };

  /**
   * Renders the section for the given files and role.
   * @param options the attachments the backend returns and whether the user is an ADMIN
   */
  async function render(options: { list?: Attachment[]; admin?: boolean }): Promise<void> {
    urlCounter = 0;
    createUrl = vi.fn(() => `blob:${++urlCounter}`);
    revokeUrl = vi.fn();
    vi.stubGlobal(
      'URL',
      Object.assign(URL, { createObjectURL: createUrl, revokeObjectURL: revokeUrl }),
    );
    service = {
      listAttachments: vi.fn().mockReturnValue(of(options.list ?? [])),
      downloadAttachment: vi.fn().mockReturnValue(of(new Blob(['x']))),
      uploadAttachments: vi.fn(),
      deleteAttachment: vi.fn(),
    };
    dialog = { open: vi.fn() };
    notification = { success: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ClientAttachments],
      providers: [
        { provide: ClientService, useValue: service },
        { provide: MatDialog, useValue: dialog },
        { provide: NotificationService, useValue: notification },
        { provide: AuthService, useValue: { isAdmin: () => options.admin ?? false } },
      ],
    });
    fixture = TestBed.createComponent(ClientAttachments);
    fixture.componentRef.setInput('clientId', 7);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  }

  /**
   * Simulates choosing files in the upload picker.
   * @param files the files to choose
   */
  async function pick(files: File[]): Promise<void> {
    const input = el.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: files, configurable: true });
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  /**
   * Finds a button by its aria-label.
   * @param label the label
   * @returns the button, or null if not shown
   */
  function byLabel(label: string): HTMLButtonElement | null {
    return el.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`);
  }

  afterEach(() => vi.unstubAllGlobals());

  it('should list the files of the client with size and uploader', async () => {
    await render({ list: [pdf] });
    expect(service.listAttachments).toHaveBeenCalledWith(7);
    const text = el.querySelector('mat-list-item')!.textContent!;
    expect(text).toContain('contract.pdf');
    expect(text).toContain('2 KB');
    expect(text).toContain('user1');
  });

  it('should show an empty message without files', async () => {
    await render({});
    expect(el.textContent).toContain('No files attached.');
  });

  describe('as USER', () => {
    it('should offer Download but neither Upload nor Delete', async () => {
      await render({ list: [pdf] });
      expect(byLabel('Download contract.pdf')).not.toBeNull();
      expect(byLabel('Delete contract.pdf')).toBeNull();
      expect(el.querySelector('input[type="file"]')).toBeNull();
      expect(el.textContent).not.toContain('Upload');
    });

    it('should download through the service and revoke the temporary URL', async () => {
      await render({ list: [pdf] });
      const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

      byLabel('Download contract.pdf')!.click();

      expect(service.downloadAttachment).toHaveBeenCalledWith(7, 1);
      expect(click).toHaveBeenCalled();
      expect(createUrl).toHaveBeenCalledTimes(1);
      expect(revokeUrl).toHaveBeenCalledWith('blob:1');
      click.mockRestore();
    });

    it('should download when the file name is clicked', async () => {
      await render({ list: [pdf] });
      const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

      el.querySelector<HTMLButtonElement>('button.file-name')!.click();

      expect(service.downloadAttachment).toHaveBeenCalledWith(7, 1);
      expect(click).toHaveBeenCalled();
      expect(revokeUrl).toHaveBeenCalledWith('blob:1');
      click.mockRestore();
    });
  });

  describe('as ADMIN', () => {
    it('should offer Upload, Download and Delete', async () => {
      await render({ list: [pdf], admin: true });
      expect(el.textContent).toContain('Upload');
      expect(el.querySelector('input[type="file"]')!.getAttribute('accept')).toContain('.pdf');
      expect(byLabel('Download contract.pdf')).not.toBeNull();
      expect(byLabel('Delete contract.pdf')).not.toBeNull();
    });

    it('should upload valid files immediately and add them sorted by name', async () => {
      await render({ list: [pdf], admin: true });
      const added: Attachment = { ...pdf, id: 9, fileName: 'a.txt', contentType: 'text/plain' };
      service.uploadAttachments.mockReturnValue(of([added]));
      const file = new File(['x'], 'a.txt', { type: 'text/plain' });

      await pick([file]);

      expect(service.uploadAttachments).toHaveBeenCalledWith(7, [file]);
      const names = Array.from(el.querySelectorAll('[matListItemTitle]')).map((n) =>
        n.textContent!.trim(),
      );
      expect(names).toEqual(['a.txt', 'contract.pdf']);
      expect(notification.success).toHaveBeenCalled();
    });

    it('should not upload and should explain when a limit is exceeded', async () => {
      await render({ list: [pdf], admin: true });
      await pick([new File(['x'], 'contract.pdf'), new File(['x'], 'bad.exe')]);
      expect(service.uploadAttachments).not.toHaveBeenCalled();
      const text = el.querySelector('.errors')!.textContent!;
      expect(text).toContain('already attached');
      expect(text).toContain('not allowed');
    });

    it('should count existing files against the 20 MB total', async () => {
      const big = { ...pdf, sizeBytes: 5 * 1024 * 1024 };
      const list = [1, 2, 3, 4].map((id) => ({ ...big, id, fileName: `f${id}.pdf` }));
      await render({ list, admin: true });
      await pick([new File(['x'], 'more.pdf')]);
      expect(service.uploadAttachments).not.toHaveBeenCalled();
      expect(el.querySelector('.errors')!.textContent).toContain('at most 20 MB');
    });

    it('should keep the list when an upload fails', async () => {
      await render({ list: [pdf], admin: true });
      service.uploadAttachments.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 413 })),
      );
      await pick([new File(['x'], 'a.txt')]);
      expect(notification.success).not.toHaveBeenCalled();
      expect(el.querySelectorAll('mat-list-item')).toHaveLength(1);
      expect(byLabel('Delete contract.pdf')!.disabled).toBe(false);
    });

    it('should warn about permanent deletion and not delete when cancelled', async () => {
      await render({ list: [pdf], admin: true });
      dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

      byLabel('Delete contract.pdf')!.click();

      const config = dialog.open.mock.calls[0];
      expect(config[0]).toBe(ConfirmDialog);
      expect(config[1].data.message).toContain('permanently deleted');
      expect(service.deleteAttachment).not.toHaveBeenCalled();
    });

    it('should delete after confirmation and remove the row and its preview URL', async () => {
      await render({ list: [png], admin: true });
      dialog.open.mockReturnValue({ afterClosed: () => of(true) });
      service.deleteAttachment.mockReturnValue(of(undefined));

      byLabel('Delete photo.png')!.click();
      await fixture.whenStable();

      expect(service.deleteAttachment).toHaveBeenCalledWith(7, 2);
      expect(el.querySelectorAll('mat-list-item')).toHaveLength(0);
      expect(revokeUrl).toHaveBeenCalledWith('blob:1');
    });
  });

  describe('pictures', () => {
    it('should show a thumbnail for JPEG/PNG/GIF/WEBP only', async () => {
      await render({ list: [pdf, png, heic] });
      expect(service.downloadAttachment).toHaveBeenCalledTimes(1);
      expect(service.downloadAttachment).toHaveBeenCalledWith(7, 2);
      const images = el.querySelectorAll('img');
      expect(images).toHaveLength(1);
      expect(images[0].getAttribute('src')).toBe('blob:1');
      expect(byLabel('Preview scan.heic')).toBeNull();
    });

    it('should open the larger preview when a thumbnail is clicked', async () => {
      await render({ list: [png] });
      byLabel('Preview photo.png')!.click();
      expect(dialog.open).toHaveBeenCalledWith(
        AttachmentPreviewDialog,
        expect.objectContaining({ data: { fileName: 'photo.png', url: 'blob:1' } }),
      );
    });

    it('should revoke all preview URLs when destroyed', async () => {
      await render({ list: [png, { ...png, id: 5, fileName: 'b.png' }] });
      expect(revokeUrl).not.toHaveBeenCalled();
      fixture.destroy();
      expect(revokeUrl.mock.calls.map((c) => c[0]).sort()).toEqual(['blob:1', 'blob:2']);
    });
  });
});
