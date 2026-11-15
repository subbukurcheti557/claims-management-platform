import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { loadRemoteModule } from '@angular-architects/native-federation';
import { AuthStore, anonymousGuard, roleGuard } from '@icmp/shared';

import { LayoutComponent } from './layout/layout.component';
import { LoginPage } from './login/login.page';
import { NotFoundPage, RemoteUnavailablePage } from './pages/fallback.pages';

/**
 * The shell decides which micro-frontend a user gets from the role in their token. Each remote is loaded at
 * runtime from its own server (see public/federation.manifest.json); if a remote is down, only that area
 * shows a fallback page and the rest of the app keeps working.
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
        loadChildren: () =>
          loadRemoteModule('claimant', './routes')
            .then((m) => m.CLAIMANT_ROUTES)
            .catch(() => [{ path: '**', component: RemoteUnavailablePage }]),
      },
      {
        path: 'staff',
        canMatch: [roleGuard('CLAIMS_OFFICER', 'CLAIMS_MANAGER')],
        loadChildren: () =>
          loadRemoteModule('staff', './routes')
            .then((m) => m.STAFF_ROUTES)
            .catch(() => [{ path: '**', component: RemoteUnavailablePage }]),
      },
      { path: '', pathMatch: 'full', redirectTo: () => inject(AuthStore).homeUrl() },
      { path: '**', component: NotFoundPage, title: 'Page not found' },
    ],
  },
];
