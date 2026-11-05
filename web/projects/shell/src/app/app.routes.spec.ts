import { demoGuard } from '@creditdesk/shared';
import { routes } from './app.routes';

describe('shell routes', () => {
  it('protects the demo with the login guard', () => {
    const demo = routes.find((r) => r.path === 'demo');
    expect(demo?.canActivate).toContain(demoGuard);
    expect(demo?.children?.map((c) => c.path)).toEqual(['', 'customers', 'proposals']);
  });

  it('keeps portfolio, resume and offline pages public', () => {
    for (const path of ['', 'resume', 'demo-offline', 'demo/signed-out']) {
      expect(routes.find((r) => r.path === path)?.canActivate).toBeUndefined();
    }
  });
});
