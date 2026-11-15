import { Routes } from '@angular/router';

import { STAFF_ROUTES } from './staff.routes';

/** Standalone (development) mode: the micro-frontend's own routes at the root. */
export const routes: Routes = [{ path: '', children: STAFF_ROUTES }];
