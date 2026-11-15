// Captures the main screens of the running app into docs/screenshots (used by the README).
// Usage: node scripts/screenshots.mjs   (stack must be running on localhost:4200)
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const OUT = new URL('../../docs/screenshots/', import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1');
mkdirSync(OUT, { recursive: true });
const BASE = 'http://localhost:4200';

const browser = await chromium.launch();

async function session(email, scheme = 'light') {
  const ctx = await browser.newContext({ viewport: { width: 1360, height: 860 }, colorScheme: scheme });
  const page = await ctx.newPage();
  await page.goto(`${BASE}/login`);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('Password1!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.waitForURL((u) => !u.pathname.startsWith('/login'));
  await page.waitForTimeout(1200);
  return { ctx, page };
}

async function shot(page, name) {
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${OUT}${name}.png`, fullPage: true });
  console.log('saved', name);
}

{
  const ctx = await browser.newContext({ viewport: { width: 1360, height: 860 } });
  const page = await ctx.newPage();
  await page.goto(`${BASE}/login`);
  await shot(page, '01-login');
  await ctx.close();
}
{
  const { ctx, page } = await session('alice@claimant.test');
  await shot(page, '02-claimant-my-claims');
  await page.getByRole('link', { name: /CLM-SG-2026-000007/ }).first().click();
  await shot(page, '03-claimant-claim-detail');
  await page.goto(`${BASE}/claimant/new`);
  await shot(page, '04-claimant-report-incident');
  await ctx.close();
}
{
  const { ctx, page } = await session('tara@claimant.test');
  await page.getByRole('link', { name: /CLM-TH-2026-000005/ }).first().click();
  await shot(page, '05-claimant-info-requested');
  await ctx.close();
}
{
  const { ctx, page } = await session('olivia@staff.test');
  await page.getByRole('tab', { name: /All open/ }).click();
  await shot(page, '06-staff-work-queue');
  await page.goto(`${BASE}/staff/claims/c0000000-0000-0000-0000-000000000007`);
  await shot(page, '07-staff-claim-workspace');
  await ctx.close();
}
{
  const { ctx, page } = await session('maya@staff.test', 'dark');
  await page.goto(`${BASE}/staff/dashboard`);
  await shot(page, '08-manager-dashboard-dark');
  await ctx.close();
}
await browser.close();
