import { expect, test } from '@playwright/test';

type OrderStatus = 'PAID' | 'PREPARING' | 'READY_FOR_PICKUP' | 'PICKED_UP';
type OrderSummary = {
  id: string;
  status: OrderStatus | 'IN_TRANSIT';
  mode: 'PICKUP' | 'DELIVERY';
  totalCents: number;
  itemCount: number;
  createdAt: string;
};

const order = (
  id: string,
  status: OrderSummary['status'],
  mode: OrderSummary['mode'] = 'PICKUP',
): OrderSummary => ({
  id,
  status,
  mode,
  totalCents: 5250,
  itemCount: 2,
  createdAt: '2026-10-09T12:00:00Z',
});

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/sessions/current', (route) =>
    route.fulfill({
      json: { id: 'admin', email: 'admin@example.test', emailVerified: true, role: 'ADMIN' },
    }),
  );
  await page.route('**/api/v1/csrf', (route) => route.fulfill({ json: { token: 'csrf-token' } }));
});

test('admin moves pickup orders through preparation, readiness, and one-time confirmation', async ({
  page,
}) => {
  const orders = [
    order('paid-order', 'PAID'),
    order('preparing-order', 'PREPARING'),
    order('ready-order', 'READY_FOR_PICKUP'),
    order('delivery-order', 'IN_TRANSIT', 'DELIVERY'),
    order('picked-up-order', 'PICKED_UP'),
  ];
  const actions: Array<{ path: string; body: unknown }> = [];

  await page.route('**/api/v1/admin/orders**', async (route) => {
    if (route.request().method() === 'GET') {
      const url = new URL(route.request().url());
      if (/\/admin\/orders\/[^/]+$/.test(url.pathname)) {
        await route.fulfill({
          json: {
            id: 'paid-order',
            items: [
              {
                productName: 'Farinha de mandioca',
                skuLabel: 'Pacote 500 g',
                quantity: 2,
                unitPriceCents: 1800,
                lineTotalCents: 3600,
              },
            ],
          },
        });
        return;
      }
      await route.fulfill({
        json: { content: orders, page: 0, size: 50, totalElements: orders.length, totalPages: 1 },
      });
      return;
    }
    const url = new URL(route.request().url());
    actions.push({ path: url.pathname, body: route.request().postDataJSON() });
    if (url.pathname.endsWith('/pickup/prepare')) {
      const target = orders.find((candidate) => candidate.id === 'paid-order')!;
      target.status = 'PREPARING';
    } else if (url.pathname.endsWith('/pickup/ready')) {
      const target = orders.find((candidate) => candidate.id === 'preparing-order')!;
      target.status = 'READY_FOR_PICKUP';
    } else if (url.pathname.endsWith('/pickup/confirm')) {
      const target = orders.find((candidate) => candidate.id === 'ready-order')!;
      target.status = 'PICKED_UP';
    }
    await route.fulfill({ status: 204 });
  });

  await page.goto('/admin/shipping');
  await expect(page.getByRole('heading', { name: 'Expedição e retirada' })).toBeVisible();
  await expect(page.getByText('paid-order')).toBeVisible();
  await expect(page.getByText('delivery-order')).toHaveCount(0);
  const completed = page.getByRole('article', { name: /Pedido picked-up-order/ });
  await expect(completed).toContainText('Retirado');
  await expect(
    completed.getByRole('button', { name: /Iniciar preparação|Marcar pronto|Confirmar retirada/ }),
  ).toHaveCount(0);

  const paid = page.getByRole('article', { name: /Pedido paid-order/ });
  await paid.getByRole('button', { name: 'Ver itens' }).click();
  await expect(paid).toContainText('Farinha de mandioca');
  await expect(paid).toContainText('Pacote 500 g');
  await paid.getByRole('button', { name: 'Iniciar preparação' }).click();
  await expect(paid).toContainText('Em preparação');
  await page
    .getByRole('article', { name: /Pedido preparing-order/ })
    .getByRole('button', { name: 'Marcar pronto para retirada' })
    .click();
  await expect(page.getByRole('article', { name: /Pedido preparing-order/ })).toContainText(
    'Pronto para retirada',
  );

  const ready = page.getByRole('article', { name: /Pedido ready-order/ });
  await ready.getByRole('button', { name: 'Confirmar retirada' }).click();
  await expect(page.getByText(/Peça ao cliente o código de retirada/)).toBeVisible();
  const confirm = page.getByRole('button', { name: 'Registrar retirada' });
  await expect(confirm).toBeDisabled();
  await page.getByLabel('Código de retirada informado pelo cliente').fill('ABCD234567');
  await confirm.click();

  await expect(ready).toContainText('Retirado');
  expect(actions).toEqual([
    { path: '/api/v1/admin/orders/paid-order/pickup/prepare', body: null },
    { path: '/api/v1/admin/orders/preparing-order/pickup/ready', body: null },
    {
      path: '/api/v1/admin/orders/ready-order/pickup/confirm',
      body: { code: 'ABCD234567' },
    },
  ]);
});

test('a changed order state is reported and can be refreshed', async ({ page }) => {
  let status: OrderSummary['status'] = 'PAID';
  await page.route('**/api/v1/admin/orders**', async (route) => {
    if (route.request().method() === 'GET') {
      const url = new URL(route.request().url());
      if (/\/admin\/orders\/[^/]+$/.test(url.pathname)) {
        await route.fulfill({ json: { id: 'race-order', items: [] } });
        return;
      }
      await route.fulfill({
        json: {
          content: [order('race-order', status)],
          page: 0,
          size: 50,
          totalElements: 1,
          totalPages: 1,
        },
      });
      return;
    }
    status = 'PREPARING';
    await route.fulfill({ status: 409, contentType: 'application/problem+json', json: {} });
  });

  await page.goto('/admin/shipping');
  await page
    .getByRole('article', { name: /Pedido race-order/ })
    .getByRole('button', { name: 'Iniciar preparação' })
    .click();
  await expect(page.getByRole('alert')).toContainText('O pedido mudou de estado');
  await page.getByRole('button', { name: 'Atualizar pedidos' }).click();
  await expect(page.getByRole('article', { name: /Pedido race-order/ })).toContainText(
    'Em preparação',
  );
  await expect(
    page
      .getByRole('article', { name: /Pedido race-order/ })
      .getByRole('button', { name: 'Iniciar preparação' }),
  ).toHaveCount(0);
});
