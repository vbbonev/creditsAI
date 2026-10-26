import { TestBed } from '@angular/core/testing';
import { AuthUnavailable } from './auth-unavailable';

describe('AuthUnavailable', () => {
  it('should explain that the login service is unavailable and offer a retry', async () => {
    const fixture = TestBed.createComponent(AuthUnavailable);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('mat-card-title')?.textContent).toContain('Login service unavailable');
    expect(el.querySelector('button')?.textContent).toContain('Try again');
  });
});
