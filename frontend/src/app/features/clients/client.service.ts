import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../shared/models/page';
import { Client, ClientQuery, ClientRequest } from './client.model';

/**
 * Calls the backend's `/api/clients` endpoints (a Gateway to the REST API). Errors are reported
 * to the user by the HTTP error interceptor and rethrown to the caller.
 */
@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/clients`;

  /**
   * Loads one page of clients.
   * @param query search text, archive filter, paging and sorting
   * @returns Observable emitting the requested page
   */
  list(query: ClientQuery): Observable<Page<Client>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', `${query.sort},${query.direction}`);
    const search = query.search.trim();
    if (search) {
      params = params.set('search', search);
    }
    if (query.includeArchived) {
      params = params.set('includeArchived', true);
    }
    return this.http.get<Page<Client>>(this.baseUrl, { params });
  }

  /**
   * Loads one client.
   * @param id the client id
   * @returns Observable emitting the client
   */
  get(id: number): Observable<Client> {
    return this.http.get<Client>(`${this.baseUrl}/${id}`);
  }

  /**
   * Registers a new client.
   * @param request the client data
   * @returns Observable emitting the created client
   */
  create(request: ClientRequest): Observable<Client> {
    return this.http.post<Client>(this.baseUrl, request);
  }

  /**
   * Replaces all editable fields of a client (ADMIN only).
   * @param id the client id
   * @param request the client data
   * @returns Observable emitting the updated client
   */
  update(id: number, request: ClientRequest): Observable<Client> {
    return this.http.put<Client>(`${this.baseUrl}/${id}`, request);
  }

  /**
   * Archives a client (ADMIN only); nothing is physically deleted.
   * @param id the client id
   * @returns Observable that completes once the client is archived
   */
  archive(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /**
   * Restores an archived client (ADMIN only).
   * @param id the client id
   * @returns Observable emitting the restored client
   */
  restore(id: number): Observable<Client> {
    return this.http.post<Client>(`${this.baseUrl}/${id}/restore`, null);
  }

  /**
   * Registers a new client together with its files as one multipart request (atomic: if a file
   * is rejected, the client is not created).
   * @param request the client data, sent as the JSON part `client`
   * @param files the files, sent as the parts `files` (may be empty)
   * @returns Observable emitting the created client
   */
  createWithFiles(request: ClientRequest, files: readonly File[]): Observable<Client> {
    const body = new FormData();
    body.append('client', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    files.forEach((file) => body.append('files', file, file.name));
    return this.http.post<Client>(this.baseUrl, body);
  }
}
