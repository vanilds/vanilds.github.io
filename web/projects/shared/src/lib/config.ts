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

/** Public demo on the EC2 cluster (Phase 6). Offline outside weekdays 08:00–20:00 São Paulo. */
export const PUBLIC_ENVIRONMENT: CreditDeskEnvironment = {
  apiUrl: 'https://creditdesk.duckdns.org',
  authority: 'https://creditdesk.duckdns.org/auth/realms/creditdesk',
  clientId: 'creditdesk-web',
  remotes: {
    customers: 'https://creditdesk.duckdns.org/mfe/customers/remoteEntry.json',
    proposals: 'https://creditdesk.duckdns.org/mfe/proposals/remoteEntry.json',
  },
};

/** Same build, two environments: decided by where the page is served from, not by a rebuild. */
export function environmentFor(hostname: string): CreditDeskEnvironment {
  return hostname === 'localhost' || hostname === '127.0.0.1'
    ? LOCAL_ENVIRONMENT
    : PUBLIC_ENVIRONMENT;
}
