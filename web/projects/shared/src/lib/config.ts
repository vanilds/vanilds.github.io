import { InjectionToken } from '@angular/core';

/**
 * Where the demo lives. One object per environment, given by whoever bootstraps Angular:
 * the shell (GitHub Pages or localhost:4200) or a micro frontend running alone (localhost:4201/4202).
 */
export interface CreditDeskEnvironment {
  /** API base URL, without trailing slash. Every request to it gets the access token. */
  apiUrl: string;
  /** Keycloak realm URL (the token's "iss"). */
  authority: string;
  /** Public OIDC client (Authorization Code + PKCE, no secret). */
  clientId: string;
  /** remoteEntry.json of each micro frontend. Fetched only when someone opens the demo. */
  remotes: { customers: string; proposals: string };
}

export const CREDITDESK_ENV = new InjectionToken<CreditDeskEnvironment>('CREDITDESK_ENV');

/** Local stack from `docker compose up` (see compose.yaml). */
export const LOCAL_ENVIRONMENT: CreditDeskEnvironment = {
  apiUrl: 'http://localhost:8080',
  authority: 'http://localhost:8180/realms/creditdesk',
  clientId: 'creditdesk-web',
  remotes: {
    customers: 'http://localhost:4201/remoteEntry.json',
    proposals: 'http://localhost:4202/remoteEntry.json',
  },
};

/**
 * A cluster serves everything from one origin, split by path (k8s/base/ingress.yaml):
 * `/api` → API, `/auth` → Keycloak, `/mfe/*` → micro frontends, `/` → shell.
 */
export function clusterEnvironment(origin: string): CreditDeskEnvironment {
  return {
    apiUrl: origin,
    authority: `${origin}/auth/realms/creditdesk`,
    clientId: 'creditdesk-web',
    remotes: {
      customers: `${origin}/mfe/customers/remoteEntry.json`,
      proposals: `${origin}/mfe/proposals/remoteEntry.json`,
    },
  };
}

/**
 * True only for calls to our API (`<apiUrl>/api/...`). On a cluster the API, Keycloak and the micro frontends share
 * one origin, so "starts with apiUrl" is not enough: the API-only headers (X-Correlation-Id, the bearer token) would
 * also go to Keycloak. From GitHub Pages that turns Keycloak's discovery into a CORS preflight that Keycloak rejects
 * (it does not allow X-Correlation-Id), and the demo looked offline while the cluster was up.
 */
export function isApiRequest(env: Pick<CreditDeskEnvironment, 'apiUrl'>, url: string): boolean {
  return url.startsWith(`${env.apiUrl}/api/`);
}

/** Public demo on the EC2 cluster (Phase 6). Offline outside weekdays 08:00–20:00 São Paulo. */
export const PUBLIC_ENVIRONMENT: CreditDeskEnvironment = clusterEnvironment('https://creditdesk.duckdns.org');

/** Ports of the Angular dev servers (`npm run start:all`), which talk to the docker compose stack. */
const DEV_SERVER_PORTS = ['4200', '4201', '4202'];

/**
 * Same build everywhere: the environment is decided by where the page is served from, not by a rebuild.
 * - Angular dev server on localhost → docker compose stack
 * - GitHub Pages (vanilds.github.io) → public demo cluster
 * - anything else is a cluster serving the shell itself (kind in Codespaces, the EC2 domain…) → same origin
 */
export function environmentFor(location: Pick<Location, 'hostname' | 'port' | 'origin'>): CreditDeskEnvironment {
  const isLocalHost = location.hostname === 'localhost' || location.hostname === '127.0.0.1';
  if (isLocalHost && DEV_SERVER_PORTS.includes(location.port)) {
    return LOCAL_ENVIRONMENT;
  }
  if (location.hostname === 'vanilds.github.io') {
    return PUBLIC_ENVIRONMENT;
  }
  return clusterEnvironment(location.origin);
}
