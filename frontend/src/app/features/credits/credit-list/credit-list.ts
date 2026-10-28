import { Component, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, Subject, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import {
  CREDIT_STATUS_LABELS,
  CREDIT_TYPE_LABELS,
  CreditQuery,
  CreditSortField,
  CreditStatus,
  CreditSummary,
  CreditType,
} from '../credit.model';
import { CreditService } from '../credit.service';

/** Delay between the last keystroke in the search box and the request, in milliseconds. */
export const SEARCH_DEBOUNCE_MS = 300;

/**
 * Paged, sortable list of credits with a search box (credit number, client name or identifier)
 * and a status filter. ADMIN can also list archived credits next to active ones. A row opens the
 * credit's detail page; "New credit" opens the credit form page.
 */
@Component({
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatSortModule,
    MatTableModule,
    RouterLink,
  ],
  selector: 'app-credit-list',
  styleUrl: './credit-list.scss',
  templateUrl: './credit-list.html',
})
export class CreditList implements OnInit {
  private readonly creditService = inject(CreditService);
  private readonly router = inject(Router);

  /** Typed search text, debounced before it reaches {@link query}. */
  private readonly searchInput = new Subject<string>();
  /** Every query to load; `switchMap` drops the answer of an outdated one. */
  private readonly requests = new Subject<CreditQuery>();

  /** Whether the current user may list archived credits. */
  protected readonly isAdmin = inject(AuthService).isAdmin();

  /** Table columns, in display order. */
  protected readonly columns = [
    'creditNumber',
    'client',
    'type',
    'amount',
    'interestRate',
    'startDate',
    'endDate',
    'monthlyPayment',
    'status',
  ];

  /** Page sizes offered by the paginator. */
  protected readonly pageSizeOptions = [10, 20, 50];

  /** The current search, filters, page and sort. */
  protected readonly query = signal<CreditQuery>({
    search: '',
    status: null,
    includeArchived: false,
    page: 0,
    size: 10,
    sort: 'creditNumber',
    direction: 'asc',
  });

  /** Text in the search box; it reaches {@link query} only after the debounce. */
  protected searchText = '';

  /** Rows of the current page. */
  protected readonly credits = signal<CreditSummary[]>([]);

  /** Number of credits matching the query, over all pages. */
  protected readonly total = signal(0);

  /** True while a page is loading. */
  protected readonly loading = signal(false);

  /**
   * Wires the debounced search box and the request stream.
   */
  constructor() {
    this.searchInput
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        map((search) => search.trim()),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((search) => this.changeQuery({ search, page: 0 }));

    this.requests
      .pipe(
        // a failed load is already reported by the error interceptor; keep the stream alive
        switchMap((query) => this.creditService.list(query).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page) {
          this.credits.set(page.content);
          this.total.set(page.page.totalElements);
        }
      });
  }

  /**
   * Loads the first page.
   */
  ngOnInit(): void {
    this.load();
  }

  /**
   * Gives the user-facing name of a credit type.
   * @param type the credit type
   * @returns the name, e.g. "Mortgage"
   */
  protected typeLabel(type: CreditType): string {
    return CREDIT_TYPE_LABELS[type];
  }

  /**
   * Gives the user-facing name of a credit status.
   * @param status the credit status
   * @returns the name, e.g. "Active"
   */
  protected statusLabel(status: CreditStatus): string {
    return CREDIT_STATUS_LABELS[status];
  }

  /**
   * Passes a keystroke in the search box on to the debounced search.
   * @param search the current text of the search box
   */
  protected onSearch(search: string): void {
    this.searchInput.next(search);
  }

  /**
   * Filters by status and starts again on the first page.
   * @param status the chosen status, or null for all
   */
  protected onStatusChange(status: CreditStatus | null): void {
    this.changeQuery({ status, page: 0 });
  }

  /**
   * Shows or hides archived credits and starts again on the first page.
   * @param event the toggle's change event
   */
  protected onIncludeArchivedChange(event: MatSlideToggleChange): void {
    this.changeQuery({ includeArchived: event.checked, page: 0 });
  }

  /**
   * Loads another page or page size.
   * @param event the paginator's page event
   */
  protected onPage(event: PageEvent): void {
    this.changeQuery({ page: event.pageIndex, size: event.pageSize });
  }

  /**
   * Sorts by the clicked column and starts again on the first page.
   * @param sort the column and direction chosen in the table header
   */
  protected onSort(sort: Sort): void {
    this.changeQuery({
      sort: sort.active as CreditSortField,
      direction: sort.direction || 'asc',
      page: 0,
    });
  }

  /**
   * Opens a credit's detail page.
   * @param credit the clicked row
   */
  protected open(credit: CreditSummary): void {
    void this.router.navigate(['/credits', credit.id]);
  }

  /**
   * Merges a change into the query and reloads.
   * @param change the query fields that changed
   */
  private changeQuery(change: Partial<CreditQuery>): void {
    this.query.update((query) => ({ ...query, ...change }));
    this.load();
  }

  /**
   * Requests the page for the current query.
   */
  private load(): void {
    this.loading.set(true);
    this.requests.next(this.query());
  }
}
