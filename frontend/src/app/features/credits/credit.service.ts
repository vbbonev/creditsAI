import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../shared/models/page';
import {
  ClientSummary,
  Credit,
  CreditCalculationRequest,
  CreditQuery,
  CreditRequest,
  CreditStatus,
  CreditSummary,
  PaymentPlan,
} from './credit.model';

/** How many clients the autocomplete asks for. */
export const CLIENT_SUGGESTIONS = 10;

/**
 * Calls the backend's `/api/credits` endpoints (a Gateway to the REST API), plus the client
 * search for the client autocomplete. Errors are reported to the user by the HTTP error
 * interceptor and rethrown to the caller.
 */
@Injectable({ providedIn: 'root' })
export class CreditService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/credits`;

  /**
   * Loads one page of credits.
   * @param query search text, status filter, archive filter, paging and sorting
   * @returns Observable emitting the requested page
   */
  list(query: CreditQuery): Observable<Page<CreditSummary>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', `${query.sort},${query.direction}`);
    const search = query.search.trim();
    if (search) {
      params = params.set('search', search);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.includeArchived) {
      params = params.set('includeArchived', true);
    }
    return this.http.get<Page<CreditSummary>>(this.baseUrl, { params });
  }

  /**
   * Loads one credit with its payment plan.
   * @param id the credit id
   * @returns Observable emitting the credit
   */
  get(id: number): Observable<Credit> {
    return this.http.get<Credit>(`${this.baseUrl}/${id}`);
  }

  /**
   * Calculates a payment plan without saving anything.
   * @param request the inputs of the plan
   * @returns Observable emitting the plan
   */
  calculate(request: CreditCalculationRequest): Observable<PaymentPlan> {
    return this.http.post<PaymentPlan>(`${this.baseUrl}/calculate`, request);
  }

  /**
   * Registers a new credit; the server calculates the plan.
   * @param request the credit data
   * @returns Observable emitting the created credit
   */
  create(request: CreditRequest): Observable<Credit> {
    return this.http.post<Credit>(this.baseUrl, request);
  }

  /**
   * Replaces all editable fields of a credit and regenerates its plan (ADMIN only).
   * @param id the credit id
   * @param request the credit data
   * @returns Observable emitting the updated credit
   */
  update(id: number, request: CreditRequest): Observable<Credit> {
    return this.http.put<Credit>(`${this.baseUrl}/${id}`, request);
  }

  /**
   * Activates or deactivates a credit (ADMIN only).
   * @param id the credit id
   * @param status the new status
   * @returns Observable emitting the updated credit
   */
  changeStatus(id: number, status: CreditStatus): Observable<Credit> {
    return this.http.post<Credit>(`${this.baseUrl}/${id}/status`, { status });
  }

  /**
   * Archives a credit (ADMIN only); nothing is physically deleted. The backend refuses an active credit.
   * @param id the credit id
   * @returns Observable that completes once the credit is archived
   */
  archive(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /**
   * Restores an archived credit (ADMIN only); it stays inactive.
   * @param id the credit id
   * @returns Observable emitting the restored credit
   */
  restore(id: number): Observable<Credit> {
    return this.http.post<Credit>(`${this.baseUrl}/${id}/restore`, null);
  }

  /**
   * Searches active clients by name or identifier for the client autocomplete.
   * @param search the typed text
   * @returns Observable emitting the best matches, by name
   */
  searchClients(search: string): Observable<ClientSummary[]> {
    const params = new HttpParams()
      .set('search', search.trim())
      .set('page', 0)
      .set('size', CLIENT_SUGGESTIONS)
      .set('sort', 'name,asc');
    return this.http
      .get<Page<ClientSummary>>(`${environment.apiUrl}/clients`, { params })
      .pipe(
        map((page) => page.content.map(({ id, name, identifier }) => ({ id, name, identifier }))),
      );
  }
}
