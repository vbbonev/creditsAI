import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Attachment } from './attachment.model';

/** The REST resources whose records can have files; the value is the URL segment. */
export type AttachmentResource = 'clients' | 'credits';

/**
 * Calls the attachment endpoints `/api/{clients|credits}/{id}/attachments` (a Gateway to the REST
 * API); clients and credits share the same contract. Errors are reported to the user by the HTTP
 * error interceptor and rethrown to the caller.
 */
@Injectable({ providedIn: 'root' })
export class AttachmentService {
  private readonly http = inject(HttpClient);

  /**
   * Builds the base URL of the attachments of one record.
   * @param resource the kind of record
   * @param ownerId the record id
   * @returns the URL without a trailing slash
   */
  private url(resource: AttachmentResource, ownerId: number): string {
    return `${environment.apiUrl}/${resource}/${ownerId}/attachments`;
  }

  /**
   * Lists the files attached to a record, sorted by name.
   * @param resource the kind of record
   * @param ownerId the record id
   * @returns Observable emitting the attachments
   */
  list(resource: AttachmentResource, ownerId: number): Observable<Attachment[]> {
    return this.http.get<Attachment[]>(this.url(resource, ownerId));
  }

  /**
   * Downloads the bytes of an attachment with the bearer token (never a plain link).
   * @param resource the kind of record
   * @param ownerId the record id
   * @param attachmentId the attachment id
   * @returns Observable emitting the file content
   */
  download(resource: AttachmentResource, ownerId: number, attachmentId: number): Observable<Blob> {
    return this.http.get(`${this.url(resource, ownerId)}/${attachmentId}/content`, {
      responseType: 'blob',
    });
  }

  /**
   * Adds files to an existing record (ADMIN only).
   * @param resource the kind of record
   * @param ownerId the record id
   * @param files the files, sent as the parts `files`
   * @returns Observable emitting the created attachments
   */
  upload(
    resource: AttachmentResource,
    ownerId: number,
    files: readonly File[],
  ): Observable<Attachment[]> {
    const body = new FormData();
    files.forEach((file) => body.append('files', file, file.name));
    return this.http.post<Attachment[]>(this.url(resource, ownerId), body);
  }

  /**
   * Permanently deletes an attachment (ADMIN only); unlike records, files are not archived.
   * @param resource the kind of record
   * @param ownerId the record id
   * @param attachmentId the attachment id
   * @returns Observable that completes once the file is deleted
   */
  delete(resource: AttachmentResource, ownerId: number, attachmentId: number): Observable<void> {
    return this.http.delete<void>(`${this.url(resource, ownerId)}/${attachmentId}`);
  }
}
