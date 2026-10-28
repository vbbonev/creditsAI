import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { NotificationService } from '../../../shared/notification/notification.service';
import { FileSizePipe, formatFileSize } from '../../../shared/pipes/file-size.pipe';
import {
  AttachmentPreviewData,
  AttachmentPreviewDialog,
} from '../attachment-preview-dialog/attachment-preview-dialog';
import { ATTACHMENT_ACCEPT, ATTACHMENT_LIMITS } from '../attachment.constants';
import { Attachment } from '../attachment.model';
import { fileIcon, isPreviewable, validateFiles } from '../attachment.validation';
import { ClientService } from '../client.service';

/**
 * Attachments section of the client detail page. Everybody can list and download files; JPEG, PNG,
 * GIF and WEBP pictures get a thumbnail and a larger preview dialog. Only ADMIN sees Upload and
 * Delete (the backend enforces this too). File bytes are fetched with the bearer token and shown
 * through object URLs, which are revoked when a file is deleted and when the component is destroyed.
 */
@Component({
  imports: [
    DatePipe,
    FileSizePipe,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatProgressBarModule,
  ],
  selector: 'app-client-attachments',
  styleUrl: './client-attachments.scss',
  templateUrl: './client-attachments.html',
})
export class ClientAttachments implements OnInit {
  private readonly clientService = inject(ClientService);
  private readonly dialog = inject(MatDialog);
  private readonly notification = inject(NotificationService);

  /** The client whose files are shown. */
  readonly clientId = input.required<number>();

  /** Whether the current user may upload and delete. */
  protected readonly isAdmin = inject(AuthService).isAdmin();

  /** Value for the file input's `accept` attribute. */
  protected readonly accept = ATTACHMENT_ACCEPT;

  /** The limits, shown as a hint. */
  protected readonly limits = ATTACHMENT_LIMITS;

  /** The limit sizes formatted for the hint text. */
  protected readonly limitsText = `Up to ${ATTACHMENT_LIMITS.maxFiles} files, ${formatFileSize(ATTACHMENT_LIMITS.maxFileBytes)} each, ${formatFileSize(ATTACHMENT_LIMITS.maxTotalBytes)} in total.`;

  /** The client's files. */
  protected readonly attachments = signal<Attachment[]>([]);

  /** True while the list is loading. */
  protected readonly loading = signal(true);

  /** True while an upload, delete or download runs; disables the buttons. */
  protected readonly busy = signal(false);

  /** Messages of the last rejected selection; empty when the selection was fine. */
  protected readonly errors = signal<string[]>([]);

  /** Object URLs of the thumbnails by attachment id. */
  protected readonly thumbnails = signal<Record<number, string>>({});

  constructor() {
    inject(DestroyRef).onDestroy(() => this.revokeAll());
  }

  /**
   * Loads the files of the client.
   */
  ngOnInit(): void {
    this.clientService.listAttachments(this.clientId()).subscribe({
      next: (list) => {
        this.attachments.set(list);
        this.loading.set(false);
        list.forEach((attachment) => this.loadThumbnail(attachment));
      },
      error: () => this.loading.set(false),
    });
  }

  /**
   * Picks the icon for a file type.
   * @param attachment the file
   * @returns the Material icon name
   */
  protected icon(attachment: Attachment): string {
    return fileIcon(attachment.contentType);
  }

  /**
   * Validates the chosen files against the limits and uploads them if they are fine.
   * @param event the change event of the file input
   */
  protected onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length === 0) {
      return;
    }
    const errors = validateFiles(files, this.attachments());
    this.errors.set(errors);
    if (errors.length > 0) {
      return;
    }
    this.busy.set(true);
    this.clientService.uploadAttachments(this.clientId(), files).subscribe({
      next: (created) => {
        this.attachments.update((list) =>
          [...list, ...created].sort((a, b) => a.fileName.localeCompare(b.fileName)),
        );
        created.forEach((attachment) => this.loadThumbnail(attachment));
        this.busy.set(false);
        this.notification.success(
          created.length === 1 ? 'File was uploaded.' : 'Files were uploaded.',
        );
      },
      error: () => this.busy.set(false),
    });
  }

  /**
   * Downloads a file with the bearer token and hands it to the browser as a save action.
   * @param attachment the file to download
   */
  protected download(attachment: Attachment): void {
    this.busy.set(true);
    this.clientService.downloadAttachment(this.clientId(), attachment.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = attachment.fileName;
        link.click();
        URL.revokeObjectURL(url);
        this.busy.set(false);
      },
      error: () => this.busy.set(false),
    });
  }

  /**
   * Opens the larger preview of a picture.
   * @param attachment the picture; ignored if its thumbnail is not loaded
   */
  protected preview(attachment: Attachment): void {
    const url = this.thumbnails()[attachment.id];
    if (url) {
      this.dialog.open<AttachmentPreviewDialog, AttachmentPreviewData>(AttachmentPreviewDialog, {
        data: { fileName: attachment.fileName, url },
        maxWidth: '90vw',
      });
    }
  }

  /**
   * Asks for confirmation, then permanently deletes the file.
   * @param attachment the file to delete
   */
  protected remove(attachment: Attachment): void {
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
        data: {
          title: 'Delete file?',
          message: `"${attachment.fileName}" will be permanently deleted. This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.busy.set(true);
        this.clientService.deleteAttachment(this.clientId(), attachment.id).subscribe({
          next: () => {
            this.revokeThumbnail(attachment.id);
            this.attachments.update((list) => list.filter((a) => a.id !== attachment.id));
            this.busy.set(false);
            this.notification.success(`File "${attachment.fileName}" was deleted.`);
          },
          error: () => this.busy.set(false),
        });
      });
  }

  /**
   * Fetches the picture of a previewable file and stores its object URL; other files are skipped.
   * @param attachment the file
   */
  private loadThumbnail(attachment: Attachment): void {
    if (!isPreviewable(attachment.contentType)) {
      return;
    }
    this.clientService.downloadAttachment(this.clientId(), attachment.id).subscribe({
      next: (blob) =>
        this.thumbnails.update((urls) => ({ ...urls, [attachment.id]: URL.createObjectURL(blob) })),
    });
  }

  /**
   * Revokes and forgets the object URL of one thumbnail.
   * @param id the attachment id
   */
  private revokeThumbnail(id: number): void {
    const { [id]: url, ...rest } = this.thumbnails();
    if (url) {
      URL.revokeObjectURL(url);
      this.thumbnails.set(rest);
    }
  }

  /**
   * Revokes every object URL, so the browser can free the memory.
   */
  private revokeAll(): void {
    Object.values(this.thumbnails()).forEach((url) => URL.revokeObjectURL(url));
  }
}
