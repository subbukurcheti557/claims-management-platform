import { expect, test } from '@playwright/test';

import { DAMAGE_PHOTO, signInWithDemo, watchErrors } from './support';

test.describe('Claimant', () => {
  test('reports an incident through the wizard and sees it tracked', async ({ page }) => {
    const errors = watchErrors(page);
    await signInWithDemo(page, /Alice Tan/);

    await expect(page).toHaveURL(/\/claimant$/);
    await expect(page.getByRole('heading', { name: 'My claims' })).toBeVisible();
    await expect(page.locator('.claim').first()).toBeVisible();

    await page.getByRole('link', { name: 'Report an incident' }).first().click();
    await expect(page.getByRole('heading', { name: 'Report an incident' })).toBeVisible();

    // Step 1: policy (lapsed policy is not offered)
    await page.getByRole('radio', { name: /SG-MOT-1001/ }).check();
    await expect(page.getByText(/Not shown: 1 inactive policy/)).toBeVisible();
    await page.getByRole('region', { name: 'Choose the policy' }).getByRole('button', { name: 'Continue' }).click();

    // Step 2: what happened
    const yesterday = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);
    await page.getByLabel('Date of the incident').fill(yesterday);
    await page.getByLabel('Where did it happen?').fill('Changi Airport car park, level 3');
    await page.getByLabel('Describe what happened').fill('Too short');
    await page.getByLabel('Where did it happen?').focus();
    await expect(page.getByText('Please give a little more detail')).toBeVisible();
    await page
      .getByLabel('Describe what happened')
      .fill('A trolley rolled into the front passenger door while I was loading luggage. Deep dent and scratches.');
    await page.getByRole('region', { name: 'What happened' }).getByRole('button', { name: 'Continue' }).click();

    // Step 3: loss and review
    await page.getByLabel('Estimated cost of the damage').fill('2400');
    await expect(page.getByRole('region', { name: 'Review', exact: true })).toContainText('Changi Airport car park');
    // a photo of the damage, attached before submitting (uploaded once the claim exists)
    await page.getByTestId('fnol-photos').setInputFiles(DAMAGE_PHOTO);
    await expect(page.getByText('damaged-door.jpg')).toBeVisible();
    await page.getByRole('button', { name: 'Submit claim' }).click();

    // Lands on the new claim, coverage confirmed synchronously
    await expect(page).toHaveURL(/\/claimant\/[0-9a-f-]{36}\?submitted=1/);
    await expect(page.getByText(/Claim CLM-SG-\d{4}-\d{6} submitted/)).toBeVisible();
    await expect(page.getByLabel('Status: Submitted')).toBeVisible();
    await expect(page.getByRole('list', { name: 'Claim progress' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Withdraw this claim' })).toBeVisible();

    // the photo is on the claim, fetched with the token and shown as a thumbnail
    const documents = page.getByTestId('claim-documents');
    await expect(documents.getByRole('img', { name: 'Damage photo: damaged-door.jpg' })).toBeVisible();
    // a PDF is not a damage photo: refused in the browser before anything is sent
    await documents.getByTestId('document-file').setInputFiles({
      name: 'scan.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.4\n'),
    });
    await expect(documents.getByRole('alert')).toContainText('scan.pdf is not a photo');

    // Kafka -> notification-service -> bell (the bell refreshes on reload)
    await expect(async () => {
      await page.reload();
      await page.getByRole('button', { name: /Notifications, [1-9]\d* unread/ }).click();
      await expect(page.getByRole('menuitem', { name: /Claim received/ }).first()).toBeVisible({ timeout: 2000 });
    }).toPass({ timeout: 30_000 });

    expect(errors).toEqual([]);
  });

  test('cannot open the staff area', async ({ page }) => {
    await signInWithDemo(page, /Bob Wong/);
    await expect(page).toHaveURL(/\/claimant$/);
    await page.goto('/staff/queue');
    await expect(page).toHaveURL(/\/claimant$/);
  });
});
