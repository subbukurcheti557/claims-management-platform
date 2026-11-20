import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { API_BASE_URL, AppNotification } from '@icmp/shared';

import { LoginPage } from './login/login.page';
import { NotificationStore } from './notifications/notification.store';
import { ThemeService } from './theme/theme.service';

const API = 'http://gw.test';

function setup() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: API }],
  });
  return TestBed.inject(HttpTestingController);
}

const note = (id: string, read = false): AppNotification => ({
  id,
  eventType: 'InfoRequested',
  category: 'ACTION_REQUIRED',
  claimId: 'c-1',
  claimNumber: 'CLM-SG-2026-000100',
  title: 'Action required',
  message: 'Please send photos',
  createdAt: '2026-11-15T10:00:00Z',
  read,
});

describe('NotificationStore', () => {
  afterEach(() => vi.useRealTimers());

  it('polls the unread count and stops cleanly', () => {
    vi.useFakeTimers();
    const http = setup();
    const store = TestBed.inject(NotificationStore);

    store.start();
    http.expectOne(`${API}/api/notifications/unread-count`).flush({ count: 2 });
    expect(store.unread()).toBe(2);

    vi.advanceTimersByTime(NotificationStore.POLL_MS);
    http.expectOne(`${API}/api/notifications/unread-count`).flush({ count: 3 });
    expect(store.unread()).toBe(3);

    store.stop();
    vi.advanceTimersByTime(NotificationStore.POLL_MS * 2);
    http.expectNone(`${API}/api/notifications/unread-count`);
    expect(store.unread()).toBe(0);
  });

  it('marks one or all notifications as read', () => {
    const http = setup();
    const store = TestBed.inject(NotificationStore);
    store.unread.set(2);
    store.loadList();
    http.expectOne((r) => r.url === `${API}/api/notifications`).flush({ content: [note('a'), note('b')] });
    expect(store.items()).toHaveLength(2);

    store.markRead(store.items()[0]);
    http.expectOne(`${API}/api/notifications/a/read`).flush(note('a', true));
    expect(store.items()[0].read).toBe(true);
    expect(store.unread()).toBe(1);

    store.markAllRead();
    http.expectOne(`${API}/api/notifications/read-all`).flush({ updated: 1 });
    expect(store.items().every((n) => n.read)).toBe(true);
    expect(store.unread()).toBe(0);
  });
});

describe('LoginPage', () => {
  beforeEach(() => sessionStorage.clear());

  it('does not call the API when the form is invalid', async () => {
    const http = setup();
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
    await fixture.whenStable();
    http.expectNone(`${API}/api/auth/login`);
    expect(fixture.nativeElement.textContent).toContain('Email is required.');
  });

  it('shows the server message on failure and navigates home on success', async () => {
    const http = setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    const type = (selector: string, value: string) => {
      const input = el.querySelector(selector) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };

    type('#email', 'alice@claimant.test');
    type('#password', 'wrong');
    (el.querySelector('button[type=submit]') as HTMLButtonElement).click();
    http
      .expectOne(`${API}/api/auth/login`)
      .flush({ detail: 'Invalid email or password.' }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('Invalid email or password.');

    type('#password', 'Password1!');
    (el.querySelector('button[type=submit]') as HTMLButtonElement).click();
    http.expectOne(`${API}/api/auth/login`).flush({
      accessToken: 't',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: { id: '1', email: 'alice@claimant.test', fullName: 'Alice Tan', role: 'CLAIMANT', market: 'SG' },
    });
    expect(navigate).toHaveBeenCalledWith('/claimant');
  });
});

describe('ThemeService', () => {
  it('cycles system -> light -> dark -> system and applies a class', () => {
    TestBed.configureTestingModule({});
    const theme = TestBed.inject(ThemeService);
    theme.apply('system');
    theme.cycle();
    expect(document.documentElement.classList.contains('theme-light')).toBe(true);
    theme.cycle();
    expect(document.documentElement.classList.contains('theme-dark')).toBe(true);
    theme.cycle();
    expect(document.documentElement.className).not.toMatch(/theme-/);
  });
});
