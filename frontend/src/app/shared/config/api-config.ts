import { InjectionToken } from '@angular/core';

/** Base URL of the API gateway. The only backend address the browser knows about. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => (globalThis as { __ICMP_API__?: string }).__ICMP_API__ ?? 'http://localhost:8080',
});
