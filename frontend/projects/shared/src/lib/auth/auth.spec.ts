import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';

import { API_BASE_URL } from '../config/api-config';
import { LoginResponse } from '../models/api.models';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from './auth.store';
import { anonymousGuard, roleGuard } from './role.guard';

const API = 'http://gw.test';

const officerLogin: LoginResponse = {
  accessToken: 'token-123',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: { id: 'u-1', email: 'olivia@staff.test', fullName: 'Olivia Lim', role: 'CLAIMS_OFFICER', market: 'SG' },
};

describe('auth', () => {
  let store: AuthStore;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: API },
      ],
    });
    store = TestBed.inject(AuthStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function signIn(response = officerLogin): void {
    store.login('olivia@staff.test', 'pw').subscribe();
    http.expectOne(`${API}/api/auth/login`).flush(response);
  }

  describe('AuthStore', () => {
    it('stores the session and derives the role flags', () => {
      signIn();
      expect(store.isAuthenticated()).toBe(true);
      expect(store.isStaff()).toBe(true);
      expect(store.isManager()).toBe(false);
      expect(store.homeUrl()).toBe('/staff');
      expect(JSON.parse(sessionStorage.getItem('icmp.session')!).token).toBe('token-123');
    });

    it('routes claimants to their own area', () => {
      signIn({ ...officerLogin, user: { ...officerLogin.user, role: 'CLAIMANT' } });
      expect(store.isClaimant()).toBe(true);
      expect(store.homeUrl()).toBe('/claimant');
    });

    it('logout clears memory and storage', () => {
      signIn();
      store.logout();
      expect(store.isAuthenticated()).toBe(false);
      expect(store.homeUrl()).toBe('/login');
      expect(sessionStorage.getItem('icmp.session')).toBeNull();
    });

    it('drops an expired session', () => {
      signIn();
      expect(store.checkExpiry(Date.now() + 3601 * 1000)).toBe(false);
      expect(store.isAuthenticated()).toBe(false);
    });
  });

  describe('authInterceptor', () => {
    it('adds the bearer token and a correlation id to gateway calls', () => {
      signIn();
      TestBed.inject(HttpClient).get(`${API}/api/claims/mine`).subscribe();
      const req = http.expectOne(`${API}/api/claims/mine`);
      expect(req.request.headers.get('Authorization')).toBe('Bearer token-123');
      expect(req.request.headers.get('X-Correlation-Id')).toMatch(/[0-9a-f-]{36}/);
      req.flush({});
    });

    it('does not send the token to other hosts', () => {
      signIn();
      TestBed.inject(HttpClient).get('https://fonts.example/font.css').subscribe();
      const req = http.expectOne('https://fonts.example/font.css');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush('');
    });

    it('signs out and goes to login when the API answers 401', () => {
      signIn();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      TestBed.inject(HttpClient).get(`${API}/api/claims/mine`).subscribe({ error: () => undefined });
      http.expectOne(`${API}/api/claims/mine`).flush({}, { status: 401, statusText: 'Unauthorized' });
      expect(store.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: 1 } });
    });
  });

  describe('guards', () => {
    const run = (guard: ReturnType<typeof roleGuard>) =>
      TestBed.runInInjectionContext(() => guard({} as never, [], {} as never)) as boolean | UrlTree;

    it('lets the right role in and sends others to their home', () => {
      signIn();
      expect(run(roleGuard('CLAIMS_OFFICER', 'CLAIMS_MANAGER'))).toBe(true);
      expect(String(run(roleGuard('CLAIMANT')))).toBe('/staff');
    });

    it('sends anonymous users to login', () => {
      expect(String(run(roleGuard('CLAIMANT')))).toBe('/login');
    });

    it('keeps signed-in users away from the login page', () => {
      expect(run(anonymousGuard)).toBe(true);
      signIn();
      expect(String(run(anonymousGuard))).toBe('/staff');
    });
  });
});
