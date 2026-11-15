import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api-config';
import { AppNotification, Page, Policy, Role, StaffMember } from '../models/api.models';

/** policy-service client. */
@Injectable({ providedIn: 'root' })
export class PolicyApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/api/policies`;

  mine(): Observable<Policy[]> {
    return this.http.get<Policy[]>(`${this.base}/mine`);
  }

  get(policyNumber: string): Observable<Policy> {
    return this.http.get<Policy>(`${this.base}/${encodeURIComponent(policyNumber)}`);
  }
}

/** identity-service staff directory client. */
@Injectable({ providedIn: 'root' })
export class StaffApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/api/users`;

  list(role?: Role): Observable<StaffMember[]> {
    const params = role ? new HttpParams().set('role', role) : undefined;
    return this.http.get<StaffMember[]>(this.base, { params });
  }
}

/** notification-service client. */
@Injectable({ providedIn: 'root' })
export class NotificationApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/api/notifications`;

  list(unreadOnly = false, size = 20): Observable<Page<AppNotification>> {
    const params = new HttpParams().set('unreadOnly', unreadOnly).set('size', size);
    return this.http.get<Page<AppNotification>>(this.base, { params });
  }

  unreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.base}/unread-count`);
  }

  markRead(id: string): Observable<AppNotification> {
    return this.http.post<AppNotification>(`${this.base}/${id}/read`, {});
  }

  markAllRead(): Observable<{ updated: number }> {
    return this.http.post<{ updated: number }>(`${this.base}/read-all`, {});
  }
}
