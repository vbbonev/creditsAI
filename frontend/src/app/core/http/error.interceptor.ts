import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { hasFieldErrors, isProblemDetail } from '../../shared/models/problem-detail';
import { AuthService } from '../auth/auth.service';
import { isApiUrl } from './api-url';

/** Shown when the server can't be reached at all (status 0: network down, request blocked). */
export const OFFLINE_MESSAGE = "Can't reach the server. Check your connection and try again.";
/** Shown for 502/503/504 from nginx while the backend is down or restarting. */
export const UNAVAILABLE_MESSAGE =
  'The server is not available right now. Please try again in a moment.';
/** Shown for any other error that carries no readable `detail`. */
export const GENERIC_MESSAGE = 'Something went wrong. Please try again.';

/** How long an error snackbar stays open, in milliseconds. */
const SNACKBAR_DURATION = 6000;

/**
 * Picks the message for the user from a failed API response.
 * @param error the failed response
 * @returns the backend's `detail`, or a fallback chosen by status
 */
function messageFor(error: HttpErrorResponse): string {
  if (isProblemDetail(error.error) && error.error.detail) {
    return error.error.detail;
  }
  if (error.status === 0) {
    return OFFLINE_MESSAGE;
  }
  if ([502, 503, 504].includes(error.status)) {
    return UNAVAILABLE_MESSAGE;
  }
  return GENERIC_MESSAGE;
}

/**
 * Central handling of failed API calls (Chain of Responsibility: one link in the HttpClient chain).
 * - 401: the session expired and the token could not be refreshed, so log in again.
 * - 400 with an `errors` map: left to the form, which shows each message on its field.
 * - anything else: the backend's `detail` (or a fallback) in a snackbar.
 * The error is always rethrown, so callers can still react (e.g. stop a spinner).
 * Requests that don't go to our API are passed through untouched.
 * @param req the outgoing request
 * @param next the next handler in the chain
 * @returns the response stream, with errors reported to the user
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApiUrl(req.url)) {
    return next(req);
  }
  const auth = inject(AuthService);
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        if (error.status === 401) {
          void auth.login();
        } else if (!(error.status === 400 && hasFieldErrors(error.error))) {
          snackBar.open(messageFor(error), 'Close', { duration: SNACKBAR_DURATION });
        }
      }
      return throwError(() => error);
    }),
  );
};
