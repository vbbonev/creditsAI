import { SortDirection } from '../../shared/models/page';

/** A client as returned by `GET /api/clients` and `GET /api/clients/{id}`. Dates are ISO-8601 strings. */
export interface Client {
  id: number;
  name: string;
  identifier: string;
  address: string;
  description: string | null;
  archived: boolean;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Body of `POST /api/clients` and `PUT /api/clients/{id}`; PUT replaces every field. */
export interface ClientRequest {
  name: string;
  identifier: string;
  address: string;
  description: string | null;
}

/** Fields the backend allows sorting the client list by. */
export type ClientSortField = 'name' | 'identifier' | 'createdAt' | 'updatedAt' | 'archivedAt';

/** What the client list asks the backend for. */
export interface ClientQuery {
  /** Text to find in the name or identifier; blank lists every client. */
  search: string;
  /** Whether archived clients are listed together with active ones (ADMIN only). */
  includeArchived: boolean;
  /** Zero-based page index. */
  page: number;
  /** Rows per page. */
  size: number;
  sort: ClientSortField;
  direction: SortDirection;
}

/** Maximum field lengths, mirroring the backend's `ClientRequest` constraints. */
export const CLIENT_MAX_LENGTH = {
  name: 100,
  identifier: 50,
  address: 200,
  description: 500,
} as const;
