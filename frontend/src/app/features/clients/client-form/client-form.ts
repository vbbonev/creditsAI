import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { applyServerErrors } from '../../../shared/forms/server-errors';
import { hasFieldErrors } from '../../../shared/models/problem-detail';
import { NotBlankValidator } from '../../../shared/validators/not-blank.validator';
import { Client, CLIENT_MAX_LENGTH, ClientRequest } from '../client.model';
import { ClientService } from '../client.service';

/** Data passed to the client form dialog. */
export interface ClientFormData {
  /** The client to edit; omitted to create a new one. */
  client?: Client;
}

/** The form's model: like {@link ClientRequest}, but the description is always a string. */
interface ClientFormModel {
  name: string;
  identifier: string;
  address: string;
  description: string;
}

/**
 * Dialog with the template-driven form for creating or editing a client. It saves through
 * {@link ClientService} and closes with the saved client; on a 400 it shows the backend's field
 * messages on the matching controls and stays open.
 */
@Component({
  imports: [
    FormsModule,
    MatButtonModule,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormFieldModule,
    MatInputModule,
    NotBlankValidator,
  ],
  selector: 'app-client-form',
  styleUrl: './client-form.scss',
  templateUrl: './client-form.html',
})
export class ClientForm {
  private readonly clientService = inject(ClientService);
  private readonly dialogRef = inject<MatDialogRef<ClientForm, Client>>(MatDialogRef);
  private readonly client = inject<ClientFormData>(MAT_DIALOG_DATA).client;

  /** Maximum field lengths, used by the `maxlength` validators and the counters. */
  protected readonly maxLength = CLIENT_MAX_LENGTH;

  /** Whether an existing client is being edited. */
  protected readonly isEdit = this.client !== undefined;

  /** Values bound to the form controls with `ngModel`. */
  protected readonly model: ClientFormModel = {
    name: this.client?.name ?? '',
    identifier: this.client?.identifier ?? '',
    address: this.client?.address ?? '',
    description: this.client?.description ?? '',
  };

  /** True while the save request is running; disables the buttons. */
  protected readonly saving = signal(false);

  /**
   * Saves the client if the form is valid, then closes the dialog with the result.
   * @param form the template-driven form
   */
  protected save(form: NgForm): void {
    if (form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    const request = this.toRequest();
    const call = this.client
      ? this.clientService.update(this.client.id, request)
      : this.clientService.create(request);
    call.subscribe({
      next: (saved) => this.dialogRef.close(saved),
      error: (error: unknown) => {
        this.saving.set(false);
        if (
          error instanceof HttpErrorResponse &&
          error.status === 400 &&
          hasFieldErrors(error.error)
        ) {
          applyServerErrors(form, error.error.errors);
        }
      },
    });
  }

  /**
   * Builds the request body; an empty description is sent as null.
   * @returns the request for the backend
   */
  private toRequest(): ClientRequest {
    const { name, identifier, address, description } = this.model;
    return { name, identifier, address, description: description.trim() ? description : null };
  }
}
