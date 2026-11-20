import { Routes } from '@angular/router';

/** The staff area, lazily loaded at /staff for claims officers and managers. */
export const STAFF_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'queue' },
  {
    path: 'queue',
    title: 'Work queue',
    loadComponent: () => import('./queue/work-queue.page').then((m) => m.WorkQueuePage),
  },
  {
    path: 'claims/:id',
    title: 'Claim workspace',
    loadComponent: () => import('./workspace/claim-workspace.page').then((m) => m.ClaimWorkspacePage),
  },
  {
    path: 'dashboard',
    title: 'Dashboard',
    loadComponent: () => import('./dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
];
