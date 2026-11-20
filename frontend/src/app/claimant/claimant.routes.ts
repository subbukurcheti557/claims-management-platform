import { Routes } from '@angular/router';

/**
 * The claimant area, lazily loaded at /claimant for users with the CLAIMANT role. Links inside it are relative.
 */
export const CLAIMANT_ROUTES: Routes = [
  {
    path: '',
    title: 'My claims',
    loadComponent: () => import('./my-claims/my-claims.page').then((m) => m.MyClaimsPage),
  },
  {
    path: 'new',
    title: 'Report an incident',
    loadComponent: () => import('./report-claim/report-claim.page').then((m) => m.ReportClaimPage),
  },
  {
    path: ':id',
    title: 'Claim details',
    loadComponent: () => import('./claim-detail/claim-detail.page').then((m) => m.ClaimDetailPage),
  },
];
