import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { environmentFor, provideCreditDeskAuth } from '@creditdesk/shared';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(), // route params and ?query → component inputs, in the shell and in the remotes
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
    ),
    // dev servers → docker compose; vanilds.github.io → public demo; a cluster serving the shell → same origin
    provideCreditDeskAuth(environmentFor(window.location)),
  ],
};
