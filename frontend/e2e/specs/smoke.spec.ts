import { expect, test } from '@playwright/test';

/** Fumaça mínima sobre a aplicação real (C10); jornadas chegam em C35+. */
test('homepage renders the app shell', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('app-root')).toBeVisible();
  await expect(page).toHaveTitle(/frontend/i);
});
