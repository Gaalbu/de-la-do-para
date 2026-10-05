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

test('accepts a purchase idempotently and only shows payment success from the order API', async ({
  page,
}) => {
  const purchaseKeys: string[] = [];
  let orderReads = 0;
  await page.route('**/api/v1/checkout/snapshots', (route) =>
    route.fulfill({ json: { snapshotId: 'snapshot-1', snapshotVersion: 3 } }),
  );
  await page.route('**/api/v1/checkout/snapshot-1/delivery-options**', (route) =>
    route.fulfill({ json: { snapshotId: 'snapshot-1', snapshotVersion: 3, options: [] } }),
  );
  await page.route('**/api/v1/checkout/snapshot-1/pickup-options**', (route) =>
    route.fulfill({
      json: {
        status: 'AVAILABLE',
        options: [
          {
            id: 'PONTO-DEMO-BELEM',
            point: 'Ponto de demonstração — Belém',
            window: 'segunda a sexta',
            preparationDays: 1,
          },
        ],
        unavailableSkuIds: [],
      },
    }),
  );
  await page.route('**/api/v1/checkout/snapshot-1/pickup-selection**', (route) =>
    route.fulfill({ json: { id: 'PONTO-DEMO-BELEM' } }),
  );
  await page.route('**/api/v1/checkout/snapshot-1/summary**', (route) =>
    route.fulfill({
      json: {
        snapshotId: 'snapshot-1',
        snapshotVersion: 3,
        lines: [
          {
            skuId: 'sku-1',
            productName: 'Farinha d’água',
            salesUnit: 'pacote',
            quantity: 2,
            unitPriceCents: 1800,
            lineTotalCents: 3600,
          },
        ],
        fulfillment: {
          mode: 'PICKUP',
          optionId: 'PONTO-DEMO-BELEM',
          label: 'Ponto de demonstração — Belém',
          shippingCents: 0,
          preparationDays: 1,
          deliveryDays: null,
        },
        couponCode: null,
        subtotalCents: 3600,
        shippingCents: 0,
        discountCents: 0,
        totalCents: 3600,
        summaryVersion: 'summary-hash',
      },
    }),
  );
  await page.route('**/api/v1/checkout/snapshot-1/purchase', async (route) => {
    purchaseKeys.push(route.request().headers()['idempotency-key']);
    await route.fulfill({
      status: 201,
      json: {
        orderId: 'order-1',
        status: 'PENDING_PAYMENT',
        totalCents: 3600,
        reservationExpiresAt: '2026-09-26T12:15:00Z',
        accessToken: 'guest-token-once',
        replayed: false,
      },
    });
  });
  await page.route('**/api/v1/orders/order-1', async (route) => {
    expect(route.request().headers()['x-order-token']).toBe('guest-token-once');
    orderReads += 1;
    await route.fulfill({
      json: {
        id: 'order-1',
        status: orderReads > 1 ? 'PAID' : 'PENDING_PAYMENT',
        totalCents: 3600,
        items: [{ productName: 'Farinha d’água', quantity: 2, lineTotalCents: 3600 }],
        payment: {
          status: orderReads > 1 ? 'CONFIRMED' : 'AWAITING_PAYMENT',
          checkoutUrl: orderReads > 1 ? null : 'https://pagamento.example/checkout-1',
          checkoutExpiresAt: '2026-09-26T12:15:00Z',
        },
      },
    });
  });

  await page.goto('/checkout');
  await page.getByLabel('CEP').fill('66053-000');
  await page.getByRole('button', { name: 'Consultar' }).click();
  await page.getByRole('button', { name: 'Selecionar' }).click();
  await page.getByLabel('E-mail para acompanhar o pedido').fill('ana@example.com');
  await expect(page.getByText('Total')).toBeVisible();
  await page.getByRole('button', { name: 'Aceitar compra' }).click();

  await expect(page).toHaveURL(/\/orders\/order-1$/);
  await expect(page.getByText('Aguardando pagamento')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Continuar para o pagamento' })).toHaveAttribute(
    'href',
    'https://pagamento.example/checkout-1',
  );
  await expect(page.getByText('Pagamento confirmado', { exact: true })).toHaveCount(0);
  expect(purchaseKeys).toHaveLength(1);
  expect(purchaseKeys[0].length).toBeGreaterThanOrEqual(16);

  await page.getByRole('button', { name: 'Atualizar status' }).click();
  await expect(page.getByRole('heading', { name: 'Pagamento confirmado' })).toBeVisible();
});

test('applies a valid coupon from the server summary and rejects an expired coupon', async ({
  page,
}) => {
  await page.route('**/api/v1/checkout/snapshots', (route) =>
    route.fulfill({ json: { snapshotId: 'snapshot-1', snapshotVersion: 3 } }),
  );
  await page.route('**/api/v1/checkout/snapshot-1/delivery-options**', (route) =>
    route.fulfill({ json: { snapshotId: 'snapshot-1', snapshotVersion: 3, options: [] } }),
  );
  await page.route('**/api/v1/checkout/snapshot-1/pickup-options**', (route) =>
    route.fulfill({
      json: {
        status: 'AVAILABLE',
        options: [
          {
            id: 'PONTO-DEMO-BELEM',
            point: 'Ponto de demonstração — Belém',
            window: 'segunda a sexta',
            preparationDays: 1,
          },
        ],
        unavailableSkuIds: [],
      },
    }),
  );
  await page.route('**/api/v1/checkout/snapshot-1/pickup-selection**', (route) =>
    route.fulfill({ json: { id: 'PONTO-DEMO-BELEM' } }),
  );
  await page.route('**/api/v1/checkout/snapshot-1/summary**', async (route) => {
    const couponCode = new URL(route.request().url()).searchParams.get('couponCode');
    if (couponCode === 'EXPIRADO') {
      await route.fulfill({ status: 422, json: { codigo: 'CHECKOUT_011' } });
      return;
    }
    await route.fulfill({
      json: {
        snapshotId: 'snapshot-1',
        snapshotVersion: 3,
        lines: [
          {
            skuId: 'sku-1',
            productName: 'Farinha d’água',
            salesUnit: 'pacote',
            quantity: 1,
            unitPriceCents: 5000,
            lineTotalCents: 5000,
          },
        ],
        fulfillment: {
          mode: 'PICKUP',
          optionId: 'PONTO-DEMO-BELEM',
          label: 'Ponto de demonstração — Belém',
          shippingCents: 0,
          preparationDays: 1,
          deliveryDays: null,
        },
        couponCode: couponCode ?? null,
        subtotalCents: 5000,
        shippingCents: 0,
        discountCents: couponCode ? 500 : 0,
        totalCents: couponCode ? 4500 : 5000,
        summaryVersion: `summary-${couponCode ?? 'none'}`,
      },
    });
  });

  await page.goto('/checkout');
  await page.getByLabel('CEP').fill('66053-000');
  await page.getByRole('button', { name: 'Consultar' }).click();
  await page
    .locator('section[aria-labelledby="pickup-title"]')
    .getByRole('button', { name: 'Selecionar' })
    .click();
  await page.getByLabel('E-mail para acompanhar o pedido').fill('cliente@example.test');

  await page.getByLabel('Cupom (opcional)').fill(' valido ');
  await page.getByRole('button', { name: 'Atualizar resumo' }).click();
  await expect(page.getByText('−R$ 5,00')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Aceitar compra' })).toBeEnabled();

  await page.getByLabel('Cupom (opcional)').fill('expirado');
  await page.getByRole('button', { name: 'Atualizar resumo' }).click();
  await expect(page.getByRole('alert')).toContainText('Não foi possível calcular o resumo');
  await expect(page.getByRole('button', { name: 'Aceitar compra' })).toHaveCount(0);
});
