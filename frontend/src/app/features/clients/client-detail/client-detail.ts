import { DatePipe } from '@angular/common';
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
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { NotificationService } from '../../../shared/notification/notification.service';
import { ClientForm, ClientFormData } from '../client-form/client-form';
import { Client } from '../client.model';
import { ClientService } from '../client.service';

/**
 * Read-only page of one client (`/clients/:id`). ADMIN gets Edit (opens {@link ClientForm}),
 * Archive (after a confirmation) and Restore. After each successful action the page returns to
 * the list and shows a success message; a refused action (e.g. 409) is reported by the HTTP
 * error interceptor and the page stays.
 */
@Component({
  imports: [
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatProgressBarModule,
    RouterLink,
  ],
  selector: 'app-client-detail',
  styleUrl: './client-detail.scss',
  templateUrl: './client-detail.html',
})
export class ClientDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly clientService = inject(ClientService);
  private readonly dialog = inject(MatDialog);
  private readonly notification = inject(NotificationService);

  /** Whether the current user may edit, archive and restore. */
  protected readonly isAdmin = inject(AuthService).isAdmin();

  /** The loaded client, or null while loading or when it could not be loaded. */
  protected readonly client = signal<Client | null>(null);

  /** True while the client is loading. */
  protected readonly loading = signal(true);

  /** True when the client does not exist or may not be seen (404, or an invalid id in the URL). */
  protected readonly notFound = signal(false);

  /** True while an archive or restore request is running; disables the buttons. */
  protected readonly busy = signal(false);

  /**
   * Loads the client named by the `id` route parameter.
   */
  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id) || id <= 0) {
      this.loading.set(false);
      this.notFound.set(true);
      return;
    }
    this.clientService.get(id).subscribe({
      next: (client) => {
        this.client.set(client);
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
   * Opens the edit dialog; after a successful save it returns to the list with a message.
   * @param client the client to edit
   */
  protected edit(client: Client): void {
    this.dialog
      .open<ClientForm, ClientFormData, Client>(ClientForm, { data: { client } })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.backToList(`Client "${saved.name}" was updated.`);
        }
      });
  }

  /**
   * Asks for confirmation, then archives the client and returns to the list with a message.
   * @param client the client to archive
   */
  protected archive(client: Client): void {
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
        data: {
          title: 'Archive client?',
          message: `"${client.name}" will be hidden from the client list. An administrator can restore it later.`,
          confirmLabel: 'Archive',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.run(this.clientService.archive(client.id), `Client "${client.name}" was archived.`);
        }
      });
  }

  /**
   * Restores an archived client and returns to the list with a message.
   * @param client the client to restore
   */
  protected restore(client: Client): void {
    this.run(this.clientService.restore(client.id), `Client "${client.name}" was restored.`);
  }

  /**
   * Runs an archive or restore request; on success returns to the list with the message.
   * @param request the request to run
   * @param successMessage the message shown after success
   */
  private run(request: Observable<unknown>, successMessage: string): void {
    this.busy.set(true);
    request.subscribe({
      next: () => this.backToList(successMessage),
      error: () => this.busy.set(false),
    });
  }

  /**
   * Navigates to the client list and shows a success message.
   * @param message the text for the user
   */
  private backToList(message: string): void {
    this.notification.success(message);
    void this.router.navigate(['/clients']);
  }
}
