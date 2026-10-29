import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { Attachments } from '../../../shared/attachments/attachments/attachments';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { saveBlob } from '../../../shared/files/save-blob';
import { NotificationService } from '../../../shared/notification/notification.service';
import { CREDIT_STATUS_LABELS, CREDIT_TYPE_LABELS, Credit, CreditStatus } from '../credit.model';
import { CreditService } from '../credit.service';
import { PaymentPlan } from '../payment-plan/payment-plan';

/**
 * Read-only page of one credit (`/credits/:id`) with its payment plan. ADMIN gets Edit (opens
 * the credit form page), Activate/Deactivate, Archive (only while INACTIVE, after a
 * confirmation) and Restore. Status changes update the page in place; archive and restore
 * return to the list with a success message. A refused action is reported by the HTTP error
 * interceptor and the page stays. USER sees no admin actions. Everyone can download the payment plan as a PDF (button beside the plan heading). A Files section (shared attachments component) lists the credit's files; only ADMIN adds or removes them.
 */
@Component({
  imports: [
    Attachments,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatProgressBarModule,
    PaymentPlan,
    RouterLink,
  ],
  selector: 'app-credit-detail',
  styleUrl: './credit-detail.scss',
  templateUrl: './credit-detail.html',
})
export class CreditDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly creditService = inject(CreditService);
  private readonly dialog = inject(MatDialog);
  private readonly notification = inject(NotificationService);

  /** Whether the current user may edit, change the status, archive and restore. */
  protected readonly isAdmin = inject(AuthService).isAdmin();

  /** User-facing names of the credit types. */
  protected readonly typeLabels = CREDIT_TYPE_LABELS;

  /** User-facing names of the credit statuses. */
  protected readonly statusLabels = CREDIT_STATUS_LABELS;

  /** The loaded credit, or null while loading or when it could not be loaded. */
  protected readonly credit = signal<Credit | null>(null);

  /** True while the credit is loading. */
  protected readonly loading = signal(true);

  /** True when the credit does not exist or may not be seen (404, or an invalid id in the URL). */
  protected readonly notFound = signal(false);

  /** True while an action request is running; disables the buttons. */
  protected readonly busy = signal(false);

  /** True while the payment plan PDF is downloading; disables its button. */
  protected readonly downloading = signal(false);

  /**
   * Loads the credit named by the `id` route parameter.
   */
  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id) || id <= 0) {
      this.loading.set(false);
      this.notFound.set(true);
      return;
    }
    this.creditService.get(id).subscribe({
      next: (credit) => {
        this.credit.set(credit);
        this.loading.set(false);
      },
      error: () => {
        // the interceptor already showed the message; show the empty state here
        this.loading.set(false);
        this.notFound.set(true);
      },
    });
  }

  /**
   * Downloads the payment plan as a PDF named `{creditNumber}-payment-plan.pdf` and hands it to the browser.
   * @param credit the credit whose plan to download
   */
  protected downloadPlan(credit: Credit): void {
    this.downloading.set(true);
    this.creditService.downloadPaymentPlan(credit.id).subscribe({
      next: (blob) => {
        saveBlob(blob, `${credit.creditNumber}-payment-plan.pdf`);
        this.downloading.set(false);
      },
      error: () => this.downloading.set(false),
    });
  }

  /**
   * Activates or deactivates the credit and shows the result in place.
   * @param credit the credit to change
   */
  protected toggleStatus(credit: Credit): void {
    const status: CreditStatus = credit.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.busy.set(true);
    this.creditService.changeStatus(credit.id, status).subscribe({
      next: (updated) => {
        this.credit.set(updated);
        this.busy.set(false);
        this.notification.success(
          `Credit ${updated.creditNumber} is now ${CREDIT_STATUS_LABELS[updated.status].toLowerCase()}.`,
        );
      },
      error: () => this.busy.set(false),
    });
  }

  /**
   * Asks for confirmation, then archives the credit and returns to the list with a message.
   * @param credit the credit to archive
   */
  protected archive(credit: Credit): void {
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
        data: {
          title: 'Archive credit?',
          message: `${credit.creditNumber} will be hidden from the credit list. An administrator can restore it later.`,
          confirmLabel: 'Archive',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.run(
            this.creditService.archive(credit.id),
            `Credit ${credit.creditNumber} was archived.`,
          );
        }
      });
  }

  /**
   * Restores an archived credit and returns to the list with a message.
   * @param credit the credit to restore
   */
  protected restore(credit: Credit): void {
    this.run(this.creditService.restore(credit.id), `Credit ${credit.creditNumber} was restored.`);
  }

  /**
   * Runs an archive or restore request; on success returns to the list with the message.
   * @param request the request to run
   * @param successMessage the message shown after success
   */
  private run(request: Observable<unknown>, successMessage: string): void {
    this.busy.set(true);
    request.subscribe({
      next: () => {
        this.notification.success(successMessage);
        void this.router.navigate(['/credits']);
      },
      error: () => this.busy.set(false),
    });
  }
}
