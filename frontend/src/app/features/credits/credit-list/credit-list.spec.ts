import { TestBed } from '@angular/core/testing';
import { CreditList } from './credit-list';

describe('CreditList', () => {
  it('should show the credits page title', async () => {
    const fixture = TestBed.createComponent(CreditList);
    await fixture.whenStable();
    const title = (fixture.nativeElement as HTMLElement).querySelector('mat-card-title');
    expect(title?.textContent).toContain('Credits');
  });
});
