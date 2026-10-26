import { environment } from '../../../environments/environment';

/**
 * Escapes the characters that have a special meaning in a regular expression.
 * @param value text to match literally
 * @returns the text with every special character backslash-escaped
 */
function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Matches requests to our backend API: `environment.apiUrl` itself, anything below it, or it with
 * a query string. Anchored, so absolute URLs to other hosts and look-alikes such as `/apix` never match.
 */
export const API_URL_PATTERN = new RegExp(`^${escapeRegExp(environment.apiUrl)}(/|\\?|$)`);

/**
 * Checks whether a request URL goes to our backend API.
 * @param url the request URL as passed to `HttpClient`
 * @returns true for API requests
 */
export function isApiUrl(url: string): boolean {
  return API_URL_PATTERN.test(url);
}
