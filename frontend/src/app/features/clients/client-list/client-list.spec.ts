import { TestBed } from '@angular/core/testing';
import { ClientList } from './client-list';

describe('ClientList', () => {
  it('should show the clients page title', async () => {
    const fixture = TestBed.createComponent(ClientList);
    await fixture.whenStable();
    const title = (fixture.nativeElement as HTMLElement).querySelector('mat-card-title');
    expect(title?.textContent).toContain('Clients');
  });
});
