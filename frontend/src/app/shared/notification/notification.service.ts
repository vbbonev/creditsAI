import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** How long a success snackbar stays open, in milliseconds. */
const SUCCESS_DURATION = 4000;

/**
 * Shows success messages in a snackbar. Error messages are not shown here; the HTTP error
 * interceptor handles them centrally.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  /**
   * Shows a short success message.
   * @param message the text for the user
   */
  success(message: string): void {
    this.snackBar.open(message, 'Close', { duration: SUCCESS_DURATION });
  }
}
