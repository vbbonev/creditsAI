import { hasFieldErrors, isProblemDetail } from './problem-detail';

describe('isProblemDetail', () => {
  it('should accept a body with a detail', () => {
    expect(isProblemDetail({ status: 409, detail: 'Client has an active credit.' })).toBe(true);
  });

  it.each([null, undefined, '<html>502 Bad Gateway</html>', {}, { detail: 42 }])(
    'should reject %s',
    (body) => {
      expect(isProblemDetail(body)).toBe(false);
    },
  );
});

describe('hasFieldErrors', () => {
  it('should accept a validation failure with an errors map', () => {
    expect(
      hasFieldErrors({ detail: 'Some fields are invalid.', errors: { name: 'must not be blank' } }),
    ).toBe(true);
  });

  it('should reject a problem without errors', () => {
    expect(hasFieldErrors({ detail: 'Not found' })).toBe(false);
  });

  it('should reject errors: null', () => {
    expect(hasFieldErrors({ detail: 'x', errors: null })).toBe(false);
  });
});
