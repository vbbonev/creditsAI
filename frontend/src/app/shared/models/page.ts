/**
 * One page of a list as returned by Spring Data (`PagedModel`): the rows in `content` and the
 * paging metadata in `page`.
 */
export interface Page<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

/** Sort direction as Spring expects it in `sort=field,direction`. */
export type SortDirection = 'asc' | 'desc';
