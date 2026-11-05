import { Routes } from '@angular/router';

/**
 * Exposed to the shell as './routes' and mounted under /demo/customers.
 * Paths are relative, so the same routes work inside the shell and when this app runs alone (port 4201).
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./list/customer-list').then((m) => m.CustomerList),
    title: 'Customers · CreditDesk',
  },
  {
    path: 'new',
    loadComponent: () => import('./form/customer-form').then((m) => m.CustomerForm),
    title: 'New customer · CreditDesk',
  },
  {
    path: ':id',
    loadComponent: () => import('./detail/customer-detail').then((m) => m.CustomerDetail),
    title: 'Customer · CreditDesk',
  },
];
