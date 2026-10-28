import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';

/** What the preview dialog shows. */
export interface AttachmentPreviewData {
  /** File name, used as heading and alternative text. */
  fileName: string;
  /** Object URL of the picture; owned (and revoked) by the opener. */
  url: string;
}

/** Dialog opened with `MatDialog` that shows one picture in a larger size. */
@Component({
  imports: [MatButtonModule, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle],
  selector: 'app-attachment-preview-dialog',
  styleUrl: './attachment-preview-dialog.scss',
  templateUrl: './attachment-preview-dialog.html',
})
export class AttachmentPreviewDialog {
  /** The picture to show. */
  protected readonly data = inject<AttachmentPreviewData>(MAT_DIALOG_DATA);
}
