import { Routes } from '@angular/router';

import { CLAIMANT_ROUTES } from './claimant.routes';

/** Standalone (development) mode: the micro-frontend's own routes at the root. */
export const routes: Routes = [{ path: '', children: CLAIMANT_ROUTES }];
