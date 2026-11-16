import { expect, test } from '@playwright/test';

import { Api, signInWithDemo, signOut, watchErrors } from './support';

test.describe('Claims staff', () => {
  test('officer takes a claim from pick-up to payment while the claimant answers a question', async ({ page }) => {
    const errors = watchErrors(page);
    const alice = await Api.as('alice@claimant.test');
    const claim = await alice.reportClaim(3200);

    // --- Officer picks it up from the queue
    await signInWithDemo(page, /Olivia Lim/);
    await expect(page).toHaveURL(/\/staff\/queue/);
    await page.getByRole('tab', { name: /To pick up/ }).click();
    const row = page.getByRole('row', { name: new RegExp(claim.claimNumber) });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: `Pick up ${claim.claimNumber}` }).click();

    await expect(page).toHaveURL(new RegExp(`/staff/claims/${claim.id}`));
    await expect(page.getByLabel('Status: Under review')).toBeVisible();

    // --- Ask the claimant for information
    await page.getByRole('radio', { name: 'Ask claimant' }).click();
    await page.getByLabel('Question for the claimant').fill('Please send a photo of the damaged mirror.');
    await page.getByRole('button', { name: 'Send question' }).click();
    await expect(page.getByLabel('Status: Information needed')).toBeVisible();
    await expect(page.getByText('Waiting for the claimant')).toBeVisible();

    // --- Claimant answers (via API to keep the test focused on the staff UI)
    const detail = await alice.get<{ infoRequests: { id: string }[] }>(`/api/claims/${claim.id}`);
    await alice.post(`/api/claims/${claim.id}/info-requests/${detail.infoRequests[0].id}/response`, {
      response: 'Photo emailed to claims@insurer.test',
    });
    await page.reload();
    await expect(page.getByLabel('Status: Under review')).toBeVisible();
    await expect(page.getByText('Photo emailed to claims@insurer.test')).toBeVisible();

    // --- Internal note
    await page.getByLabel('Add a note').fill('Workshop quote checked, in line with market rates.');
    await page.getByRole('button', { name: 'Save note' }).click();
    await expect(page.getByText('Workshop quote checked')).toBeVisible();

    // --- Assess
    await page.getByRole('radio', { name: 'Assess' }).click();
    await page.getByLabel('Recommended payment').fill('2700');
    await page.getByLabel('Reserve').fill('2700');
    await page.getByLabel('Findings').fill('Photo and quote match the reported damage.');
    await page.getByRole('button', { name: 'Record assessment' }).click();
    await expect(page.getByLabel('Status: Assessed')).toBeVisible();

    // --- No damage photo yet: approval is blocked until the claimant sends one
    await page.getByRole('radio', { name: 'Approve' }).click();
    await expect(page.getByTestId('no-photos')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Approve claim' })).toBeDisabled();
    await alice.uploadDamagePhoto(claim.id);
    await page.reload();

    // --- The officer looks at the photo full size
    const evidence = page.getByTestId('claim-documents');
    await expect(evidence.getByRole('img', { name: 'Damage photo: damaged-door.jpg' })).toBeVisible();
    await evidence.getByRole('button', { name: 'Open damaged-door.jpg' }).click();
    await expect(page.getByRole('dialog', { name: 'damaged-door.jpg' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toBeHidden();

    // --- Approve (within authority) and record payment
    await page.getByRole('radio', { name: 'Approve' }).click();
    await expect(page.getByTestId('no-photos')).toBeHidden();
    await expect(page.getByLabel('Amount to approve')).toHaveValue('2700');
    await page.getByRole('button', { name: 'Approve claim' }).click();
    await expect(page.getByLabel('Status: Approved')).toBeVisible();

    await page.getByLabel('Payment reference').fill(`PAY-UI-${Date.now()}`);
    await page.getByRole('button', { name: 'Record payment and close' }).click();
    await expect(page.getByLabel('Status: Settled')).toBeVisible();
    await expect(page.getByText('This claim is closed. No further actions.')).toBeVisible();

    // --- The claimant sees the payment
    await signOut(page);
    await signInWithDemo(page, /Alice Tan/);
    await page.goto(`/claimant/${claim.id}`);
    await expect(page.getByText(/We paid SGD 2,700.00/)).toBeVisible();

    expect(errors).toEqual([]);
    await alice.dispose();
  });

  test('officer cannot approve above the authority limit', async ({ page }) => {
    const alice = await Api.as('alice@claimant.test');
    const olivia = await Api.as('olivia@staff.test');
    // SGD 40,000 is about USD 29,600: above the USD 25,000 officer limit
    const claim = await alice.reportClaim(40000);
    await olivia.post(`/api/claims/${claim.id}/assign`, {});
    await olivia.post(`/api/claims/${claim.id}/assessment`, {
      liability: 'ACCEPTED',
      recommendedAmount: 40000,
      reserveAmount: 40000,
      findings: 'Write-off confirmed by the assessor.',
    });
    await alice.uploadDamagePhoto(claim.id);

    await signInWithDemo(page, /Olivia Lim/);
    await page.goto(`/staff/claims/${claim.id}`);
    await page.getByRole('radio', { name: 'Approve' }).click();
    await expect(page.getByText('above your approval limit of USD 25,000')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Approve claim' })).toBeDisabled();
    await alice.dispose();
    await olivia.dispose();
  });

  test('manager sees exposure by market and team workload', async ({ page }) => {
    const errors = watchErrors(page);
    await signInWithDemo(page, /Maya Chen/);
    await page.getByRole('link', { name: /Exposure & team/ }).click();

    await expect(page.getByRole('heading', { name: 'Liability exposure and team workload' })).toBeVisible();
    await expect(page.getByText('Outstanding liability')).toBeVisible();
    const markets = page.getByRole('table', { name: 'Exposure by market' }).getByRole('row');
    await expect(markets).toHaveCount(7); // header + six markets
    await expect(page.getByRole('table', { name: 'Workload per officer' })).toContainText('Olivia Lim');

    await page.getByRole('combobox', { name: 'Market' }).click();
    await page.getByRole('option', { name: 'Thailand' }).click();
    await expect(markets).toHaveCount(2);
    expect(errors).toEqual([]);
  });

  test('queue filters and theme toggle work and the skip link is present', async ({ page }) => {
    await signInWithDemo(page, /Maya Chen/);
    await page.getByRole('tab', { name: /All open/ }).click();
    await page.getByRole('switch', { name: 'Overdue only' }).click();
    const rows = page.getByRole('table', { name: 'Claims' }).getByRole('row');
    await expect(rows.nth(1)).toContainText('Overdue');

    await page.getByRole('button', { name: /Change theme/ }).click();
    await expect(page.locator('html')).toHaveClass(/theme-(light|dark)/);
    await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeAttached();
  });
});
