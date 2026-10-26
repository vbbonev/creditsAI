import { isApiUrl } from './api-url';

describe('isApiUrl', () => {
  it.each(['/api', '/api/clients', '/api/clients/1', '/api?page=0'])('should accept %s', (url) => {
    expect(isApiUrl(url)).toBe(true);
  });

  it.each([
    '/apix',
    '/API/clients',
    '/auth/realms/credits',
    'http://localhost/api/clients',
    '//evil.example.com/api/clients',
    'https://evil.example.com/api/clients',
  ])('should reject %s', (url) => {
    expect(isApiUrl(url)).toBe(false);
  });
});
