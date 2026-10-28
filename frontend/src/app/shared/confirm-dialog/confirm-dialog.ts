import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';

/** What the confirm dialog shows. */
export interface ConfirmDialogData {
  /** Dialog heading, e.g. "Archive client?". */
  title: string;
  /** Explanation of what will happen. */
  message: string;
  /** Label of the confirm button, e.g. "Archive". */
  confirmLabel: string;
}

/**
 * Generic yes/no dialog opened with `MatDialog`. It closes with `true` when the user confirms,
 * and with `undefined` when they cancel or dismiss it.
 */
@Component({
  imports: [MatButtonModule, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle],
  selector: 'app-confirm-dialog',
  templateUrl: './confirm-dialog.html',
})
export class ConfirmDialog {
  /** The texts passed by the caller. */
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
