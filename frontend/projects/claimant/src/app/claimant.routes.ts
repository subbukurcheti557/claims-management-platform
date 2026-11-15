import { Routes } from '@angular/router';

/**
 * Exposed to the shell as './routes' (see federation.config.mjs). All links inside this micro-frontend are
 * relative, so it works both mounted at /claimant in the shell and standalone on its own dev server.
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
