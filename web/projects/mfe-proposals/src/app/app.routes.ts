import { Routes } from '@angular/router';

/** Exposed to the shell as './routes' and mounted under /demo/proposals. */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./list/proposal-list').then((m) => m.ProposalList),
    title: 'Proposals · CreditDesk',
  },
  {
    path: 'new',
    loadComponent: () => import('./form/proposal-form').then((m) => m.ProposalForm),
    title: 'New proposal · CreditDesk',
  },
  {
    path: ':id',
    loadComponent: () => import('./detail/proposal-detail').then((m) => m.ProposalDetail),
    title: 'Proposal · CreditDesk',
  },
];
