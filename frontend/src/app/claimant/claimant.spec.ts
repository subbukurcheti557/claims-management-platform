import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL, ClaimDetail, ClaimSummary } from '@icmp/shared';

import { ClaimDetailStore } from './claim-detail/claim-detail.store';
import { MyClaimsStore } from './my-claims/my-claims.store';

const API = 'http://gw.test';

function setup<T>(store: Type<T>) {
  TestBed.configureTestingModule({
    providers: [
      store,
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: API },
    ],
  });
  return { store: TestBed.inject(store), http: TestBed.inject(HttpTestingController) };
}

const summary = (id: string, status: ClaimSummary['status']): ClaimSummary =>
  ({ id, claimNumber: `CLM-${id}`, status, currency: 'SGD' }) as ClaimSummary;

const detail = (patch: Partial<ClaimDetail> = {}): ClaimDetail =>
  ({
    id: 'c-1',
    claimNumber: 'CLM-SG-2026-000100',
    status: 'INFO_REQUESTED',
    allowedActions: ['RESPOND', 'WITHDRAW'],
    infoRequests: [
      { id: 'r-1', question: 'Police report?', status: 'OPEN', requestedAt: '2026-11-15T10:00:00Z' },
      { id: 'r-0', question: 'Photos?', status: 'ANSWERED', response: 'sent', requestedAt: '2026-11-14T10:00:00Z' },
    ],
    ...patch,
  }) as ClaimDetail;

describe('MyClaimsStore', () => {
  it('loads claims, counts open and closed, and flags those needing action', () => {
    const { store, http } = setup(MyClaimsStore);
    store.load();
    expect(store.loading()).toBe(true);
    http.expectOne((r) => r.url === `${API}/api/claims/mine`).flush({
      content: [summary('1', 'SUBMITTED'), summary('2', 'INFO_REQUESTED'), summary('3', 'SETTLED')],
    });

    expect(store.loading()).toBe(false);
    expect(store.counts()).toEqual({ ALL: 3, OPEN: 2, CLOSED: 1 });
    expect(store.needsAction().map((c) => c.id)).toEqual(['2']);

    store.setFilter('CLOSED');
    expect(store.claims().map((c) => c.id)).toEqual(['3']);
  });

  it('exposes a readable error', () => {
    const { store, http } = setup(MyClaimsStore);
    store.load();
    http
      .expectOne((r) => r.url === `${API}/api/claims/mine`)
      .flush({ detail: 'Service down' }, { status: 503, statusText: 'Unavailable' });
    expect(store.error()).toBe('Service down');
  });
});

describe('ClaimDetailStore', () => {
  function loaded() {
    const ctx = setup(ClaimDetailStore);
    ctx.store.load('c-1');
    ctx.http.expectOne(`${API}/api/claims/c-1`).flush(detail());
    ctx.http.expectOne(`${API}/api/claims/c-1/timeline`).flush([]);
    return ctx;
  }

  it('splits open and answered information requests', () => {
    const { store } = loaded();
    expect(store.openRequests().map((r) => r.id)).toEqual(['r-1']);
    expect(store.answeredRequests().map((r) => r.id)).toEqual(['r-0']);
    expect(store.canWithdraw()).toBe(true);
  });

  it('answers a request and refreshes from the server response', () => {
    const { store, http } = loaded();
    let done = false;
    store.respond('r-1', 'PR-123', () => (done = true));
    expect(store.busy()).toBe('r-1');

    const req = http.expectOne(`${API}/api/claims/c-1/info-requests/r-1/response`);
    expect(req.request.body).toEqual({ response: 'PR-123' });
    req.flush(detail({ status: 'UNDER_REVIEW', allowedActions: [], infoRequests: [] }));
    http.expectOne(`${API}/api/claims/c-1/timeline`).flush([{ toStatus: 'UNDER_REVIEW' }]);

    expect(done).toBe(true);
    expect(store.busy()).toBeNull();
    expect(store.claim()?.status).toBe('UNDER_REVIEW');
    expect(store.timeline()).toHaveLength(1);
  });

  it('keeps the claim and shows the error if a command is refused', () => {
    const { store, http } = loaded();
    store.withdraw('');
    http
      .expectOne(`${API}/api/claims/c-1/withdraw`)
      .flush({ detail: 'Cannot withdraw a claim that is UNDER_REVIEW.' }, { status: 409, statusText: 'Conflict' });
    expect(store.actionError()).toContain('Cannot withdraw');
    expect(store.claim()?.status).toBe('INFO_REQUESTED');
  });
});
