import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { Shell } from './shell';

describe('Shell', () => {
  let auth: {
    username: ReturnType<typeof vi.fn>;
    isAdmin: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  /**
   * Renders the shell for a user with the given role.
   * @param admin whether the user is an ADMIN
   * @returns the rendered root element
   */
  async function render(admin: boolean): Promise<HTMLElement> {
    auth = {
      username: vi.fn().mockReturnValue('user1'),
      isAdmin: vi.fn().mockReturnValue(admin),
      logout: vi.fn().mockResolvedValue(undefined),
    };
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('should show a horizontal menu with Clients and Credits links', async () => {
    const el = await render(false);
    const links = Array.from(el.querySelectorAll<HTMLAnchorElement>('nav.menu a'));
    expect(links.map((a) => a.textContent?.trim())).toEqual([
      expect.stringContaining('Clients'),
      expect.stringContaining('Credits'),
    ]);
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['/clients', '/credits']);
  });

  it('should show the username', async () => {
    const el = await render(false);
    expect(el.querySelector('.username')?.textContent).toContain('user1');
  });

  it('should not show the administrator icon to a plain USER', async () => {
    const el = await render(false);
    expect(el.querySelector('.admin')).toBeNull();
  });

  it('should show the administrator icon to an ADMIN', async () => {
    const el = await render(true);
    expect(el.querySelector('.admin')).not.toBeNull();
  });

  it('should log out when the logout button is clicked', async () => {
    const el = await render(false);
    el.querySelector<HTMLButtonElement>('button[aria-label="Log out"]')!.click();
    expect(auth.logout).toHaveBeenCalled();
  });

  it('should host the routed page', async () => {
    const el = await render(false);
    expect(el.querySelector('main router-outlet')).not.toBeNull();
  });
});
