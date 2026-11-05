import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { loadRemoteModule } from '@angular-architects/native-federation';
import { CREDITDESK_ENV, demoGuard } from '@creditdesk/shared';

/**
 * A micro frontend's routes, fetched from its own server at the moment someone opens it.
 * If that server is down, the user lands on "Demo offline" instead of a broken page.
 */
function remote(name: 'customers' | 'proposals') {
  return () => {
    const remoteEntry = inject(CREDITDESK_ENV).remotes[name];
    return loadRemoteModule({ remoteEntry, exposedModule: './routes' })
      .then((m) => m.routes)
      .catch((error) => {
        console.error(`Micro frontend "${name}" is unreachable`, error);
        return import('./demo/demo-offline').then((m) => [
          { path: '**', component: m.DemoOffline },
        ]);
      });
  };
}

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/home').then((m) => m.Home) },
  {
    path: 'resume',
    loadComponent: () => import('./pages/resume').then((m) => m.Resume),
    title: 'Resume · Vani Santos',
  },
  {
    path: 'demo-offline',
    loadComponent: () => import('./demo/demo-offline').then((m) => m.DemoOffline),
    title: 'Demo offline · CreditDesk',
  },
  {
    path: 'demo/signed-out',
    loadComponent: () => import('./demo/signed-out').then((m) => m.SignedOut),
    title: 'Signed out · CreditDesk',
  },
  {
    path: 'demo',
    canActivate: [demoGuard],
    loadComponent: () => import('./demo/demo-layout').then((m) => m.DemoLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'proposals' },
      { path: 'customers', loadChildren: remote('customers') },
      { path: 'proposals', loadChildren: remote('proposals') },
    ],
  },
  { path: '**', redirectTo: '' },
];
