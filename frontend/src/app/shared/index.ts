/*
 * @icmp/shared — code used by both the claimant and the staff areas (path alias to this folder).
 * Holds the API contract types, typed API clients, the auth session and common UI building blocks.
 */
export * from './models/api.models';
export * from './config/api-config';
export * from './auth/auth.store';
export * from './auth/auth.interceptor';
export * from './auth/role.guard';
export * from './errors/problem';
export * from './api/claims.api';
export * from './api/directory.api';
export * from './ui/status';
export * from './ui/status-chip.component';
export * from './ui/pipes';
export * from './ui/timeline.component';
export * from './ui/state.components';
export * from './documents/documents.store';
export * from './documents/claim-documents.component';
