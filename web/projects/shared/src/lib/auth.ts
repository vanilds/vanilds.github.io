import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  EnvironmentProviders,
  Injectable,
  Provider,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import {
  LogLevel,
  OidcSecurityService,
  authInterceptor,
  provideAuth,
} from 'angular-auth-oidc-client';
import { catchError, map, of, switchMap, tap, timeout } from 'rxjs';
import { CREDITDESK_ENV, CreditDeskEnvironment } from './config';
import { correlationIdInterceptor } from './correlation-id';

export type Role = 'ANALYST' | 'MANAGER' | 'AUDITOR';

export interface SessionUser {
  id: string;
  username: string;
  name: string;
  roles: Role[];
}

/**
 * Login with Keycloak: OAuth 2 Authorization Code + PKCE (RFC 7636), the flow for apps that cannot keep a secret.
 *
 * - The browser is sent to Keycloak, the user types the password **there** (never in our pages), and comes back
 *   with a one-time code. The library exchanges code + PKCE verifier for tokens.
 * - `secureRoutes`: the interceptor adds `Authorization: Bearer …` only to calls to our API. A token sent to any
 *   other host would be a leak.
 * - Refresh token + silent renew: access tokens live 5 minutes; the user does not notice the renewals.
 * - No app initializer on purpose: the portfolio must open even when Keycloak is offline. Login is checked only
 *   when someone enters the demo ({@link demoGuard}).
 *
 * Provided once by whoever bootstraps Angular. Micro frontends loaded inside the shell reuse the shell's
 * HttpClient, so they never configure auth themselves.
 */
export function provideCreditDeskAuth(
  env: CreditDeskEnvironment,
  basePath = '/demo',
): (Provider | EnvironmentProviders)[] {
  const origin = window.location.origin;
  return [
    { provide: CREDITDESK_ENV, useValue: env },
    provideHttpClient(withInterceptors([correlationIdInterceptor, authInterceptor()])),
    provideAuth({
      config: {
        authority: env.authority,
        clientId: env.clientId,
        redirectUrl: origin + basePath,
        // after the code exchange the library navigates here (default would be "/", the portfolio)
        postLoginRoute: basePath || '/',
        postLogoutRedirectUri: origin + basePath + '/signed-out',
        scope: 'openid profile',
        responseType: 'code',
        silentRenew: true,
        useRefreshToken: true,
        renewTimeBeforeTokenExpiresInSeconds: 30,
        secureRoutes: [env.apiUrl],
        logLevel: LogLevel.Warn,
      },
    }),
  ];
}

@Injectable({ providedIn: 'root' })
export class Session {
  private readonly oidc = inject(OidcSecurityService);
  private readonly current = signal<SessionUser | null>(null);

  readonly user = this.current.asReadonly();
  readonly signedIn = computed(() => this.current() !== null);

  /** Reads the session (or finishes a login coming back from Keycloak with ?code=…). */
  check() {
    return this.oidc.checkAuth().pipe(
      tap((result) =>
        this.current.set(result.isAuthenticated ? userFromToken(result.accessToken) : null),
      ),
      map((result) => result.isAuthenticated),
    );
  }

  signIn(): void {
    this.oidc.authorize();
  }

  signOut(): void {
    this.current.set(null);
    this.oidc.logoffAndRevokeTokens().subscribe();
  }

  /** A manager can do everything an analyst can (same hierarchy as the API). */
  can(role: Role): boolean {
    const roles = this.current()?.roles ?? [];
    return roles.includes(role) || (role === 'ANALYST' && roles.includes('MANAGER'));
  }
}

/** The UI hides buttons by role, but only to be helpful: the API decides. A hidden button is not security. */
export function userFromToken(accessToken: string): SessionUser | null {
  try {
    const payload = JSON.parse(base64UrlDecode(accessToken.split('.')[1]));
    const known: Role[] = ['ANALYST', 'MANAGER', 'AUDITOR'];
    const roles = ((payload.realm_access?.roles ?? []) as string[]).filter((r): r is Role =>
      known.includes(r as Role),
    );
    return {
      id: payload.sub,
      username: payload.preferred_username ?? payload.sub,
      name: payload.name ?? payload.preferred_username ?? payload.sub,
      roles,
    };
  } catch {
    return null;
  }
}

function base64UrlDecode(part: string): string {
  const base64 = part
    .replace(/-/g, '+')
    .replace(/_/g, '/')
    .padEnd(Math.ceil(part.length / 4) * 4, '=');
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/**
 * Entry to the demo. Signed in → go on. Not signed in → Keycloak's login page.
 * Keycloak unreachable (the cluster is switched off outside office hours) → "Demo offline" page,
 * instead of a blank screen or an endless spinner.
 */
export const demoGuard: CanActivateFn = () => {
  const session = inject(Session);
  const router = inject(Router);
  const http = inject(HttpClient);
  const env = inject(CREDITDESK_ENV);
  const offline = router.parseUrl('/demo-offline');
  // Ask Keycloak first: when it is down, the OIDC library just reports "not signed in", and sending the user to
  // a login page that does not exist would leave a blank tab.
  return http.get(`${env.authority}/.well-known/openid-configuration`).pipe(
    // a stopped server can leave the request hanging; after a few seconds, say so instead of waiting forever
    timeout(8000),
    switchMap(() => session.check()),
    map((signedIn) => {
      if (!signedIn) {
        session.signIn();
        return false;
      }
      return true;
    }),
    catchError(() => of(offline)),
  );
};
