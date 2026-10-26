/**
 * Error body returned by the backend (RFC 9457 `ProblemDetail`). `detail` is written for the user;
 * `errors` is only present on 400 validation failures and maps field names to messages.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: Record<string, string>;
}

/**
 * Checks whether a response body looks like a backend `ProblemDetail`.
 * @param body the parsed error body (may be HTML from nginx, a string, or null)
 * @returns true if the body is an object with a string `detail` or an `errors` map
 */
export function isProblemDetail(body: unknown): body is ProblemDetail {
  if (typeof body !== 'object' || body === null) {
    return false;
  }
  const problem = body as ProblemDetail;
  return typeof problem.detail === 'string' || typeof problem.errors === 'object';
}

/**
 * Checks whether a response body is a validation failure with per-field messages.
 * @param body the parsed error body
 * @returns true if the body carries a non-null `errors` map
 */
export function hasFieldErrors(
  body: unknown,
): body is ProblemDetail & { errors: Record<string, string> } {
  return isProblemDetail(body) && typeof body.errors === 'object' && body.errors !== null;
}
