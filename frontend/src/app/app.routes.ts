import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthStore, anonymousGuard, roleGuard } from '@icmp/shared';

import { LayoutComponent } from './layout/layout.component';
import { LoginPage } from './login/login.page';
import { NotFoundPage } from './pages/fallback.pages';

/**
 * One application with two lazily loaded areas. The role in the token decides which area a user may open;
 * each area's code is a separate chunk, downloaded only when that user first navigates to it.
 */
export const routes: Routes = [
  { path: 'login', component: LoginPage, canMatch: [anonymousGuard], title: 'Sign in' },
  {
    path: '',
    component: LayoutComponent,
    children: [
      {
        path: 'claimant',
        canMatch: [roleGuard('CLAIMANT')],
        loadChildren: () => import('./claimant/claimant.routes').then((m) => m.CLAIMANT_ROUTES),
      },
      {
        path: 'staff',
        canMatch: [roleGuard('CLAIMS_OFFICER', 'CLAIMS_MANAGER')],
        loadChildren: () => import('./staff/staff.routes').then((m) => m.STAFF_ROUTES),
      },
      { path: '', pathMatch: 'full', redirectTo: () => inject(AuthStore).homeUrl() },
      { path: '**', component: NotFoundPage, title: 'Page not found' },
    ],
  },
];
