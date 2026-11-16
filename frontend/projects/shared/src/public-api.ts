/*
 * @icmp/shared — shared between the shell and both micro-frontends as a federation singleton.
 * Holds the API contract types, typed API clients, the auth session and common UI building blocks.
 */
export * from './lib/models/api.models';
export * from './lib/config/api-config';
export * from './lib/auth/auth.store';
export * from './lib/auth/auth.interceptor';
export * from './lib/auth/role.guard';
export * from './lib/errors/problem';
export * from './lib/api/claims.api';
export * from './lib/api/directory.api';
export * from './lib/ui/status';
export * from './lib/ui/status-chip.component';
export * from './lib/ui/pipes';
export * from './lib/ui/timeline.component';
export * from './lib/ui/state.components';
export * from './lib/documents/documents.store';
export * from './lib/documents/claim-documents.component';
