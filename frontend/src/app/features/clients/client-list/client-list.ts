import { Component, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, Subject, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../shared/notification/notification.service';
import { ClientForm, ClientFormData } from '../client-form/client-form';
import { Client, ClientQuery, ClientSortField } from '../client.model';
import { ClientService } from '../client.service';

/** Delay between the last keystroke in the search box and the request, in milliseconds. */
export const SEARCH_DEBOUNCE_MS = 300;

/**
 * Paged, sortable list of clients with a search box (name or identifier). ADMIN can also list
 * archived clients next to active ones. A row opens the client's detail page; "New client"
 * opens {@link ClientForm} in a dialog.
 */
@Component({
  imports: [
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSlideToggleModule,
    MatSortModule,
    MatTableModule,
  ],
  selector: 'app-client-list',
  styleUrl: './client-list.scss',
  templateUrl: './client-list.html',
})
export class ClientList implements OnInit {
  private readonly clientService = inject(ClientService);
  private readonly dialog = inject(MatDialog);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);

  /** Typed search text, debounced before it reaches {@link query}. */
  private readonly searchInput = new Subject<string>();
  /** Every query to load; `switchMap` drops the answer of an outdated one. */
  private readonly requests = new Subject<ClientQuery>();

  /** Whether the current user may list archived clients. */
  protected readonly isAdmin = inject(AuthService).isAdmin();

  /** Table columns, in display order. */
  protected readonly columns = ['name', 'identifier', 'address'];

  /** Page sizes offered by the paginator. */
  protected readonly pageSizeOptions = [10, 20, 50];

  /** The current search, filter, page and sort. */
  protected readonly query = signal<ClientQuery>({
    search: '',
    includeArchived: false,
    page: 0,
    size: 10,
    sort: 'name',
    direction: 'asc',
  });

  /** Text in the search box; it reaches {@link query} only after the debounce. */
  protected searchText = '';

  /** Rows of the current page. */
  protected readonly clients = signal<Client[]>([]);

  /** Number of clients matching the query, over all pages. */
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
        switchMap((query) => this.clientService.list(query).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page) {
          this.clients.set(page.content);
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
   * Passes a keystroke in the search box on to the debounced search.
   * @param search the current text of the search box
   */
  protected onSearch(search: string): void {
    this.searchInput.next(search);
  }

  /**
   * Shows or hides archived clients and starts again on the first page.
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
      sort: sort.active as ClientSortField,
      direction: sort.direction || 'asc',
      page: 0,
    });
  }

  /**
   * Opens a client's detail page.
   * @param client the clicked row
   */
  protected open(client: Client): void {
    void this.router.navigate(['/clients', client.id]);
  }

  /**
   * Opens the form dialog for a new client; after a successful save it confirms and reloads.
   */
  protected create(): void {
    this.dialog
      .open<ClientForm, ClientFormData, Client>(ClientForm, { data: {} })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.notification.success(`Client "${created.name}" was created.`);
          this.load();
        }
      });
  }

  /**
   * Merges a change into the query and reloads.
   * @param change the query fields that changed
   */
  private changeQuery(change: Partial<ClientQuery>): void {
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
