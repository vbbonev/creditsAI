import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../auth/auth.service';
import {
  errorInterceptor,
  GENERIC_MESSAGE,
  OFFLINE_MESSAGE,
  UNAVAILABLE_MESSAGE,
} from './error.interceptor';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let snackBar: { open: ReturnType<typeof vi.fn> };
  let auth: { login: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    snackBar = { open: vi.fn() };
    auth = { login: vi.fn().mockResolvedValue(undefined) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackBar },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  /**
   * Sends a GET, answers it with the given error and returns what the caller received.
   * @param url request URL
   * @param body error body
   * @param status HTTP status (0 simulates a network failure)
   * @returns the error the caller's error callback got
   */
  function fail(url: string, body: string | object | null, status: number): unknown {
    let received: unknown;
    http.get(url).subscribe({ error: (e: unknown) => (received = e) });
    const req = httpTesting.expectOne(url);
    if (status === 0) {
      req.error(new ProgressEvent('error'));
    } else {
      req.flush(body, { status, statusText: 'Error' });
    }
    return received;
  }

  /**
   * Returns the message of the only snackbar that was opened.
   * @returns the snackbar text
   */
  function snackMessage(): string {
    expect(snackBar.open).toHaveBeenCalledTimes(1);
    return snackBar.open.mock.calls[0][0];
  }

  it.each([
    [409, 'Client has an active credit and cannot be archived.'],
    [404, 'Client 7 was not found.'],
    [403, 'You do not have permission to perform this action.'],
    [500, 'An unexpected error occurred. Please try again later.'],
    [400, 'Malformed request.'],
  ])("should show the backend's detail for %i", (status, detail) => {
    fail('/api/clients/7', { status, detail }, status);
    expect(snackMessage()).toBe(detail);
  });

  it('should leave a 400 with field errors to the form', () => {
    const received = fail(
      '/api/clients',
      { detail: 'Some fields are invalid.', errors: { name: 'required' } },
      400,
    );
    expect(snackBar.open).not.toHaveBeenCalled();
    expect((received as { status: number }).status).toBe(400);
  });

  it('should start the login on 401 without a snackbar', () => {
    fail('/api/clients', null, 401);
    expect(auth.login).toHaveBeenCalledTimes(1);
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('should explain a network failure', () => {
    fail('/api/clients', null, 0);
    expect(snackMessage()).toBe(OFFLINE_MESSAGE);
  });

  it.each([502, 503, 504])('should explain %i from nginx (HTML body, no detail)', (status) => {
    fail('/api/clients', '<html><body>Bad Gateway</body></html>', status);
    expect(snackMessage()).toBe(UNAVAILABLE_MESSAGE);
  });

  it('should fall back to a generic message when there is no detail', () => {
    fail('/api/clients', null, 500);
    expect(snackMessage()).toBe(GENERIC_MESSAGE);
  });

  it('should rethrow the error to the caller', () => {
    const received = fail('/api/clients/7', { detail: 'Not found' }, 404);
    expect((received as { status: number }).status).toBe(404);
  });

  it('should ignore requests that do not go to the API', () => {
    fail('/auth/realms/credits/account', null, 401);
    fail('https://example.com/data', { detail: 'x' }, 500);
    expect(auth.login).not.toHaveBeenCalled();
    expect(snackBar.open).not.toHaveBeenCalled();
  });
});
