import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL, AuthStore, ClaimDetail, ClaimSummary } from '@icmp/shared';

import { QueueStore } from './queue/queue.store';
import { ActionPanelComponent } from './workspace/action-panel.component';
import { WorkspaceStore } from './workspace/workspace.store';

const API = 'http://gw.test';

function configure(extra: unknown[] = []) {
  TestBed.configureTestingModule({
    providers: [
      ...(extra as never[]),
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: API },
    ],
  });
  return TestBed.inject(HttpTestingController);
}

function signInAs(role: 'CLAIMS_OFFICER' | 'CLAIMS_MANAGER', id = 'officer-1') {
  const http = TestBed.inject(HttpTestingController);
  TestBed.inject(AuthStore).login('x@staff.test', 'pw').subscribe();
  http.expectOne(`${API}/api/auth/login`).flush({
    accessToken: 't',
    tokenType: 'Bearer',
    expiresIn: 3600,
    user: { id, email: 'x@staff.test', fullName: 'X', role, market: 'SG' },
  });
}

const claim = (patch: Partial<ClaimDetail> = {}): ClaimDetail =>
  ({
    id: 'c-1',
    claimNumber: 'CLM-SG-2026-000100',
    status: 'ASSESSED',
    currency: 'SGD',
    version: 4,
    estimatedLoss: 50000,
    estimatedLossUsd: 37000,
    maxPayable: 59500,
    reserveAmount: 40000,
    assignedOfficerId: 'officer-1',
    allowedActions: ['REASSIGN', 'REQUEST_INFO', 'ASSESS', 'REJECT', 'APPROVE'],
    infoRequests: [],
    latestAssessment: {
      id: 'a',
      liability: 'ACCEPTED',
      recommendedAmount: 40000,
      reserveAmount: 40000,
      findings: 'ok',
      assessorName: 'X',
      assessedAt: '',
    },
    ...patch,
  }) as ClaimDetail;

describe('QueueStore', () => {
  beforeEach(() => localStorage.clear());

  it('queries the queue with scope, filters and paging', () => {
    const http = configure([QueueStore]);
    const store = TestBed.inject(QueueStore);
    store.setFilters({ market: 'TH', overdueOnly: true, q: ' CLM ' });
    const req = http.expectOne((r) => r.url === `${API}/api/claims/queue`);
    expect(req.request.params.get('scope')).toBe('UNASSIGNED');
    expect(req.request.params.get('market')).toBe('TH');
    expect(req.request.params.get('overdueOnly')).toBe('true');
    expect(req.request.params.get('q')).toBe('CLM');
    expect(req.request.params.get('sort')).toBe('slaDueAt,asc');
    req.flush({ content: [{ id: '1' }], totalElements: 1 });
    expect(store.rows()).toHaveLength(1);
    expect(store.hasFilters()).toBe(true);
  });

  it('remembers the chosen tab', () => {
    const http = configure([QueueStore]);
    TestBed.inject(QueueStore).setScope('MINE');
    http.expectOne((r) => r.url === `${API}/api/claims/queue`).flush({ content: [], totalElements: 0 });
    expect(localStorage.getItem('icmp.queue.scope')).toBe('MINE');
  });

  it('explains a lost pick-up race (409) and refreshes the queue', () => {
    const http = configure([QueueStore]);
    const store = TestBed.inject(QueueStore);
    const row = { id: 'c-9', claimNumber: 'CLM-9', version: 0 } as ClaimSummary;
    const opened = vi.fn();

    store.pickUp(row, opened);
    const req = http.expectOne(`${API}/api/claims/c-9/assign`);
    expect(req.request.body).toEqual({ expectedVersion: 0 });
    req.flush({ detail: 'changed' }, { status: 409, statusText: 'Conflict' });

    expect(opened).not.toHaveBeenCalled();
    expect(store.notice()).toContain('CLM-9 was just picked up by a colleague');
    http.expectOne((r) => r.url === `${API}/api/claims/queue`).flush({ content: [], totalElements: 0 });
  });
});

describe('WorkspaceStore', () => {
  function loaded(role: 'CLAIMS_OFFICER' | 'CLAIMS_MANAGER' = 'CLAIMS_OFFICER') {
    const http = configure([WorkspaceStore]);
    signInAs(role);
    const store = TestBed.inject(WorkspaceStore);
    store.load('c-1');
    http.expectOne(`${API}/api/claims/c-1`).flush(claim());
    http.expectOne(`${API}/api/claims/c-1/timeline`).flush([]);
    http.expectOne(`${API}/api/claims/c-1/notes`).flush([]);
    if (role === 'CLAIMS_MANAGER') http.expectOne(`${API}/api/users`).flush([]);
    return { http, store };
  }

  it('sends the version being viewed with each command', () => {
    const { http, store } = loaded();
    expect(store.canHandle()).toBe(true);
    store.approve(40000);
    const req = http.expectOne(`${API}/api/claims/c-1/approve`);
    expect(req.request.body).toEqual({ expectedVersion: 4, amount: 40000 });
    req.flush(claim({ status: 'APPROVED', version: 5 }));
    http.expectOne(`${API}/api/claims/c-1/timeline`).flush([]);
    expect(store.claim()?.status).toBe('APPROVED');
    expect(store.notice()).toContain('approved');
  });

  it('reloads the claim when someone else changed it (409)', () => {
    const { http, store } = loaded();
    store.settle('PAY-1');
    http
      .expectOne(`${API}/api/claims/c-1/settle`)
      .flush({ detail: 'Claim was changed by someone else.' }, { status: 409, statusText: 'Conflict' });
    expect(store.actionError()).toContain('latest version has been loaded');
    http.expectOne(`${API}/api/claims/c-1`).flush(claim({ version: 5 }));
    http.expectOne(`${API}/api/claims/c-1/timeline`).flush([]);
    http.expectOne(`${API}/api/claims/c-1/notes`).flush([]);
    expect(store.claim()?.version).toBe(5);
  });

  it('managers can act on any claim and get the staff list for assignment', () => {
    const { store } = loaded('CLAIMS_MANAGER');
    expect(store.canHandle()).toBe(true);
  });
});

describe('ActionPanelComponent', () => {
  async function render(isManager: boolean, patch: Partial<ClaimDetail> = {}) {
    configure();
    const fixture = TestBed.createComponent(ActionPanelComponent);
    fixture.componentRef.setInput('claim', claim(patch));
    fixture.componentRef.setInput('canHandle', true);
    fixture.componentRef.setInput('isManager', isManager);
    await fixture.whenStable();
    return fixture;
  }

  it('offers only the actions the server allows for the status', async () => {
    const fixture = await render(false);
    const labels = [...fixture.nativeElement.querySelectorAll('mat-button-toggle')].map((b: Element) =>
      b.textContent?.trim(),
    );
    expect(labels).toEqual(['Re-assess', 'Approve', 'Ask claimant', 'Reject']);
  });

  it('blocks an officer from approving above the USD 25,000 limit', async () => {
    const fixture = await render(false);
    (fixture.componentInstance as unknown as { tab: { set(v: string): void } }).tab.set('approve');
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('above your approval limit of USD 25,000');
    const button = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Approve claim')) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });

  it('lets a manager approve the same amount', async () => {
    const fixture = await render(true);
    (fixture.componentInstance as unknown as { tab: { set(v: string): void } }).tab.set('approve');
    await fixture.whenStable();
    const approved = vi.fn();
    fixture.componentInstance.approve.subscribe(approved);
    const button = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent?.includes('Approve claim')) as HTMLButtonElement;
    expect(button.disabled).toBe(false);
    button.click();
    expect(approved).toHaveBeenCalledWith(40000);
  });

  it('shows a closed claim as read-only', async () => {
    const fixture = await render(false, { status: 'SETTLED', allowedActions: [] });
    expect(fixture.nativeElement.textContent).toContain('This claim is closed');
  });
});
