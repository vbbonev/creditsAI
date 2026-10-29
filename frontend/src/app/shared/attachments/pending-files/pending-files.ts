import { Component, input, model } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { FileSizePipe, formatFileSize } from '../../pipes/file-size.pipe';
import { ATTACHMENT_ACCEPT, ATTACHMENT_LIMITS } from '../attachment.constants';

/**
 * File picker for a record that does not exist yet (new client, new credit): collects the files
 * that are sent together with the first save and lists them with a remove button. It only edits
 * the `files` model; the parent validates the files (see `validateFiles`), passes the problems in
 * `errors` and decides whether it may save. The buttons are template-driven (no form controls).
 */
@Component({
  imports: [FileSizePipe, MatButtonModule, MatIconModule],
  selector: 'app-pending-files',
  styleUrl: './pending-files.scss',
  templateUrl: './pending-files.html',
})
export class PendingFiles {
  /** The chosen files (two-way bindable). */
  readonly files = model<File[]>([]);

  /** Problems of the chosen files, shown under the list. */
  readonly errors = input<readonly string[]>([]);

  /** Disables the buttons, e.g. while saving. */
  readonly disabled = input(false);

  /** Value for the file input's `accept` attribute. */
  protected readonly accept = ATTACHMENT_ACCEPT;

  /** The limits as text, shown next to the picker. */
  protected readonly limitsText = `Up to ${ATTACHMENT_LIMITS.maxFiles} files, ${formatFileSize(ATTACHMENT_LIMITS.maxFileBytes)} each, ${formatFileSize(ATTACHMENT_LIMITS.maxTotalBytes)} in total.`;

  /**
   * Adds the chosen files to the selection.
   * @param event the change event of the file input
   */
  protected onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const added = Array.from(input.files ?? []);
    input.value = '';
    this.files.update((current) => [...current, ...added]);
  }

  /**
   * Removes one file from the selection.
   * @param file the file to remove
   */
  protected removeFile(file: File): void {
    this.files.update((current) => current.filter((f) => f !== file));
  }
}
