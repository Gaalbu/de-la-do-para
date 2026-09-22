import { expect, test } from '@playwright/test';

test('compares delivery and pickup and keeps one selected modality', async ({ page }) => {
  const requests: string[] = [];

  await page.route('**/api/v1/checkout/snapshots', async (route) => {
    requests.push('snapshot');
    await route.fulfill({ json: { snapshotId: 'snapshot-1', snapshotVersion: 3 } });
  });
  await page.route('**/api/v1/checkout/snapshot-1/delivery-options**', async (route) => {
    requests.push('delivery-options');
    await route.fulfill({
      json: {
        snapshotId: 'snapshot-1',
        snapshotVersion: 3,
        inputFingerprint: 'fingerprint-1',
        options: [
          {
            id: 'quote-1',
            inputFingerprint: 'fingerprint-1',
            serviceName: 'Sandbox PAC',
            priceCents: 2590,
            deliveryDays: 5,
            preparationDays: 2,
            packageSequences: [1],
            expiresAt: '2026-09-22T13:00:00Z',
          },
        ],
      },
    });
  });
  await page.route('**/api/v1/checkout/snapshot-1/pickup-options**', async (route) => {
    requests.push('pickup-options');
    await route.fulfill({
      json: {
        status: 'AVAILABLE',
        options: [
          {
            id: 'PONTO-DEMO-BELEM',
            point: 'Ponto de demonstração — Belém',
            window: 'segunda a sexta, das 9h às 18h (horário de Belém)',
            preparationDays: 1,
          },
        ],
        unavailableSkuIds: [],
      },
    });
  });
  await page.route('**/api/v1/checkout/snapshot-1/pickup-selection**', async (route) => {
    requests.push('pickup-selection');
    await route.fulfill({
      json: {
        id: 'PONTO-DEMO-BELEM',
        point: 'Ponto de demonstração — Belém',
        window: 'segunda a sexta, das 9h às 18h (horário de Belém)',
        preparationDays: 1,
      },
    });
  });

  await page.goto('/checkout');
  await page.getByLabel('CEP').fill('66053-000');
  await page.getByRole('button', { name: 'Consultar' }).click();

  await expect(page.getByText('Sandbox PAC')).toBeVisible();
  await expect(page.getByText('Ponto de demonstração — Belém')).toBeVisible();
  await expect(page.getByText('R$ 25,90')).toBeVisible();
  await page
    .locator('section[aria-labelledby="pickup-title"]')
    .getByRole('button', { name: 'Selecionar' })
    .click();
  await expect(page.getByRole('button', { name: 'Selecionada' })).toBeVisible();
  expect(requests).toEqual(['snapshot', 'delivery-options', 'pickup-options', 'pickup-selection']);
});
