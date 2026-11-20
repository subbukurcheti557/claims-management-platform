import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { API_BASE_URL } from '../config/api-config';
import { AuthStore } from './auth.store';

/** Adds the bearer token and a correlation id to gateway calls; signs out on 401. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  const baseUrl = inject(API_BASE_URL);

  if (!req.url.startsWith(baseUrl)) {
    return next(req);
  }
  const token = auth.token();
  const headers: Record<string, string> = { 'X-Correlation-Id': crypto.randomUUID() };
  if (token && !req.url.endsWith('/api/auth/login')) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return next(req.clone({ setHeaders: headers })).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !req.url.endsWith('/api/auth/login')) {
        auth.logout();
        void router.navigate(['/login'], { queryParams: { expired: 1 } });
      }
      return throwError(() => error);
    }),
  );
};
