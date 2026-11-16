import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api-config';
import {
  ClaimDetail,
  ClaimDocument,
  ClaimNote,
  ClaimStatus,
  ClaimSummary,
  DocumentCategory,
  Exposure,
  Liability,
  Market,
  NewClaimRequest,
  OfficerWorkload,
  Page,
  QueueQuery,
  RejectionReason,
  TimelineEntry,
  Workload,
} from '../models/api.models';

/** Typed client for claims-service (through the gateway). */
@Injectable({ providedIn: 'root' })
export class ClaimsApi {
  private readonly http = inject(HttpClient);
  private readonly gateway = inject(API_BASE_URL);
  private readonly base = `${this.gateway}/api/claims`;

  // ---------- claimant
  submit(request: NewClaimRequest): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(this.base, request);
  }

  mine(status?: ClaimStatus, page = 0, size = 20): Observable<Page<ClaimSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<Page<ClaimSummary>>(`${this.base}/mine`, { params });
  }

  respond(claimId: string, requestId: string, response: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/${claimId}/info-requests/${requestId}/response`, { response });
  }

  withdraw(claimId: string, reason?: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/${claimId}/withdraw`, { reason });
  }

  // ---------- shared
  get(claimId: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`${this.base}/${claimId}`);
  }

  timeline(claimId: string): Observable<TimelineEntry[]> {
    return this.http.get<TimelineEntry[]>(`${this.base}/${claimId}/timeline`);
  }

  // ---------- documents (both user types; the server decides what each may see)
  documents(claimId: string): Observable<ClaimDocument[]> {
    return this.http.get<ClaimDocument[]>(`${this.base}/${claimId}/documents`);
  }

  uploadDocument(claimId: string, category: DocumentCategory, file: File): Observable<ClaimDocument> {
    const form = new FormData();
    form.append('file', file, file.name);
    const params = new HttpParams().set('category', category);
    return this.http.post<ClaimDocument>(`${this.base}/${claimId}/documents`, form, { params });
  }

  /** The file itself, through the gateway with the bearer token (an <img src> could not send it). */
  documentContent(document: ClaimDocument): Observable<Blob> {
    return this.http.get(`${this.gateway}${document.contentUrl}`, { responseType: 'blob' });
  }

  // ---------- staff
  queue(query: QueueQuery): Observable<Page<ClaimSummary>> {
    let params = new HttpParams().set('scope', query.scope);
    for (const [key, value] of Object.entries(query)) {
      if (key !== 'scope' && value !== undefined && value !== null && value !== '' && value !== false) {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<ClaimSummary>>(`${this.base}/queue`, { params });
  }

  assign(claimId: string, expectedVersion: number, officerId?: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/${claimId}/assign`, { expectedVersion, officerId });
  }

  requestInfo(claimId: string, expectedVersion: number, question: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/${claimId}/info-requests`, { expectedVersion, question });
  }

  assess(
    claimId: string,
    expectedVersion: number,
    body: { liability: Liability; recommendedAmount: number; reserveAmount: number; findings: string },
  ): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/${claimId}/assessment`, { expectedVersion, ...body });
  }

  approve(claimId: string, expectedVersion: number, amount: number): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/${claimId}/approve`, { expectedVersion, amount });
  }

  settle(claimId: string, expectedVersion: number, paymentReference: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/${claimId}/settle`, { expectedVersion, paymentReference });
  }

  reject(claimId: string, expectedVersion: number, reasonCode: RejectionReason, explanation: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/${claimId}/reject`, { expectedVersion, reasonCode, explanation });
  }

  notes(claimId: string): Observable<ClaimNote[]> {
    return this.http.get<ClaimNote[]>(`${this.base}/${claimId}/notes`);
  }

  addNote(claimId: string, body: string): Observable<ClaimNote> {
    return this.http.post<ClaimNote>(`${this.base}/${claimId}/notes`, { body });
  }

  exposure(market?: Market): Observable<Exposure> {
    const params = market ? new HttpParams().set('market', market) : undefined;
    return this.http.get<Exposure>(`${this.base}/stats/exposure`, { params });
  }

  workload(): Observable<Workload> {
    return this.http.get<Workload>(`${this.base}/stats/workload`);
  }

  myStats(): Observable<OfficerWorkload> {
    return this.http.get<OfficerWorkload>(`${this.base}/stats/me`);
  }
}
