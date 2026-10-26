import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotFound } from './not-found';

describe('NotFound', () => {
  it('should say the page was not found and link to the start page', async () => {
    TestBed.configureTestingModule({ imports: [NotFound], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(NotFound);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('mat-card-title')?.textContent).toContain('Page not found');
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/');
  });
});
