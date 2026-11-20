import { HttpErrorResponse } from '@angular/common/http';

import { ProblemDetail } from '../models/api.models';

/** Turns any HTTP error into a sentence a user can act on, using the backend's RFC 7807 detail. */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'The service could not be reached. Check your connection and try again.';
    }
    const problem = error.error as ProblemDetail | null;
    if (problem && typeof problem === 'object') {
      if (problem.errors?.length) {
        return problem.errors.map((e) => `${e.field}: ${e.message}`).join('; ');
      }
      if (problem.detail) {
        return problem.detail;
      }
    }
    if (error.status === 403) return 'You are not allowed to do this.';
    if (error.status === 404) return 'This item could not be found.';
    if (error.status >= 500) return 'Something went wrong on our side. Please try again shortly.';
  }
  return 'Something went wrong. Please try again.';
}

export function isConflict(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 409;
}
