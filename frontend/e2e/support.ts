import { APIRequestContext, Page, expect, request } from '@playwright/test';

export const GATEWAY = process.env['E2E_GATEWAY_URL'] ?? 'http://localhost:8080';
export const PASSWORD = 'Password1!';

/** Fails the test on any uncaught error in the page. */
export function watchErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  return errors;
}

export async function signInWithDemo(page: Page, name: RegExp | string): Promise<void> {
  await page.goto('/login');
  await page.getByRole('button', { name }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

export async function signIn(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login/);
}

/** Direct API helper for arranging test data quickly (the UI flows under test still go through the browser). */
export class Api {
  private constructor(
    private readonly ctx: APIRequestContext,
    private readonly token: string,
  ) {}

  static async as(email: string): Promise<Api> {
    const ctx = await request.newContext({ baseURL: GATEWAY });
    const res = await ctx.post('/api/auth/login', { data: { email, password: PASSWORD } });
    expect(res.status()).toBe(200);
    return new Api(ctx, (await res.json()).accessToken);
  }

  private headers() {
    return { Authorization: `Bearer ${this.token}` };
  }

  async get<T>(path: string): Promise<T> {
    const res = await this.ctx.get(path, { headers: this.headers() });
    expect(res.ok(), `${path} -> ${res.status()}`).toBeTruthy();
    return res.json();
  }

  async post<T>(path: string, data: unknown): Promise<T> {
    const res = await this.ctx.post(path, { headers: this.headers(), data });
    expect(res.ok(), `${path} -> ${res.status()} ${await res.text()}`).toBeTruthy();
    return res.json();
  }

  async reportClaim(estimatedLoss = 3200): Promise<{ id: string; claimNumber: string }> {
    const incidentDate = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);
    return this.post('/api/claims', {
      policyNumber: 'SG-MOT-1001',
      claimType: 'MOTOR',
      incidentDate,
      incidentLocation: 'Bukit Timah Road',
      description: 'Side mirror knocked off by a passing lorry while parked. Door scratched too.',
      injuryReported: false,
      estimatedLoss,
    });
  }

  async dispose(): Promise<void> {
    await this.ctx.dispose();
  }
}
