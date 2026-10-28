import { adminGuard } from '../../core/auth/admin.guard';
import { creditsRoutes } from './credits.routes';

describe('creditsRoutes', () => {
  it('should declare "new" before ":id", so "new" is not read as an id', () => {
    const paths = creditsRoutes.map((route) => route.path);
    expect(paths.indexOf('new')).toBeLessThan(paths.indexOf(':id'));
  });

  it('should protect the edit page with the admin guard', () => {
    const edit = creditsRoutes.find((route) => route.path === ':id/edit');
    expect(edit?.canActivate).toEqual([adminGuard]);
  });

  it('should leave the list, new and detail pages open to every logged-in user', () => {
    for (const path of ['', 'new', ':id']) {
      expect(creditsRoutes.find((route) => route.path === path)?.canActivate).toBeUndefined();
    }
  });
});
