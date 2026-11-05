import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { LOCAL_ENVIRONMENT, demoGuard, provideCreditDeskAuth } from '@creditdesk/shared';
import { routes } from './app.routes';

/**
 * Only used when this micro frontend runs alone (`npm run start:proposals`, port 4202) — handy to develop one
 * screen without the shell. Inside the shell, the shell's providers are used and this file is never loaded.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      [{ path: '', canActivate: [demoGuard], children: routes }],
      withComponentInputBinding(),
    ),
    provideCreditDeskAuth(LOCAL_ENVIRONMENT, ''),
  ],
};
