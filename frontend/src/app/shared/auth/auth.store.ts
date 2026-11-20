import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { API_BASE_URL } from '../config/api-config';
import { LoginResponse, Role, UserProfile } from '../models/api.models';

interface Session {
  token: string;
  expiresAt: number;
  user: UserProfile;
}

const STORAGE_KEY = 'icmp.session';
const STAFF_ROLES: readonly Role[] = ['CLAIMS_OFFICER', 'CLAIMS_MANAGER'];

/**
 * Single source of truth for who is signed in, shared by every page of the application.
 * The session lives in sessionStorage: closing the tab signs the user out.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly session = signal<Session | null>(AuthStore.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly role = computed(() => this.session()?.user.role ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly isStaff = computed(() => {
    const role = this.role();
    return role !== null && STAFF_ROLES.includes(role);
  });
  readonly isManager = computed(() => this.role() === 'CLAIMS_MANAGER');
  readonly isClaimant = computed(() => this.role() === 'CLAIMANT');
  /** Where the user lands after sign-in. */
  readonly homeUrl = computed(() => (this.isStaff() ? '/staff' : this.isClaimant() ? '/claimant' : '/login'));

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.baseUrl}/api/auth/login`, { email, password })
      .pipe(tap((res) => this.store({ token: res.accessToken, expiresAt: Date.now() + res.expiresIn * 1000, user: res.user })));
  }

  logout(): void {
    this.session.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // storage unavailable: nothing to clear
    }
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.role();
    return role !== null && roles.includes(role);
  }

  /** Drops the session if the token has expired. Returns whether a valid session remains. */
  checkExpiry(now = Date.now()): boolean {
    const s = this.session();
    if (s && s.expiresAt <= now) {
      this.logout();
      return false;
    }
    return s !== null;
  }

  private store(session: Session): void {
    this.session.set(session);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // storage unavailable: session lasts until reload
    }
  }

  private static restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const session = JSON.parse(raw) as Session;
      return session.expiresAt > Date.now() ? session : null;
    } catch {
      return null;
    }
  }
}
