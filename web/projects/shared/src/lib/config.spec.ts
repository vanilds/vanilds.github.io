import { LOCAL_ENVIRONMENT, PUBLIC_ENVIRONMENT, environmentFor, isApiRequest } from './config';

const at = (url: string) => {
  const u = new URL(url);
  return { hostname: u.hostname, port: u.port, origin: u.origin };
};

describe('environmentFor', () => {
  it('uses the docker compose stack from the Angular dev servers', () => {
    expect(environmentFor(at('http://localhost:4200/demo'))).toBe(LOCAL_ENVIRONMENT);
    expect(environmentFor(at('http://127.0.0.1:4202/'))).toBe(LOCAL_ENVIRONMENT);
  });

  it('uses the public demo cluster from GitHub Pages', () => {
    expect(environmentFor(at('https://vanilds.github.io/demo'))).toBe(PUBLIC_ENVIRONMENT);
  });

  it('uses its own origin when a cluster serves the shell (kind in Codespaces)', () => {
    const env = environmentFor(at('https://fluffy-train-8088.app.github.dev/demo'));
    expect(env.apiUrl).toBe('https://fluffy-train-8088.app.github.dev');
    expect(env.authority).toBe('https://fluffy-train-8088.app.github.dev/auth/realms/creditdesk');
    expect(env.remotes.proposals).toBe('https://fluffy-train-8088.app.github.dev/mfe/proposals/remoteEntry.json');
  });

  it('treats localhost on another port as a cluster (kind port 8088 on your own machine)', () => {
    expect(environmentFor(at('http://localhost:8088/')).apiUrl).toBe('http://localhost:8088');
  });
});

describe('isApiRequest', () => {
  it('matches only the API on a cluster, where Keycloak and the micro frontends share the origin', () => {
    expect(isApiRequest(PUBLIC_ENVIRONMENT, 'https://creditdesk.duckdns.org/api/customers?page=0')).toBe(true);
    expect(isApiRequest(PUBLIC_ENVIRONMENT, `${PUBLIC_ENVIRONMENT.authority}/.well-known/openid-configuration`)).toBe(false);
    expect(isApiRequest(PUBLIC_ENVIRONMENT, PUBLIC_ENVIRONMENT.remotes.customers)).toBe(false);
  });

  it('matches the API of the docker compose stack', () => {
    expect(isApiRequest(LOCAL_ENVIRONMENT, 'http://localhost:8080/api/proposals')).toBe(true);
    expect(isApiRequest(LOCAL_ENVIRONMENT, `${LOCAL_ENVIRONMENT.authority}/protocol/openid-connect/token`)).toBe(false);
  });
});
