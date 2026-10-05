import { expect, test } from '@playwright/test';

const initialCoupon = {
  id: 'coupon-1',
  code: 'BEMVINDO',
  discountType: 'PERCENTAGE',
  discountValue: 10,
  minimumCents: 5000,
  validFrom: '2026-09-01T12:00:00Z',
  validUntil: '2026-10-01T12:00:00Z',
  active: true,
  globalLimit: 50,
  perEmailLimit: 1,
  globalUsage: 12,
  createdAt: '2026-09-01T12:00:00Z',
  updatedAt: '2026-09-01T12:00:00Z',
};

test('admin creates, edits and deactivates coupons without changing their codes', async ({
  page,
}) => {
  let current = { ...initialCoupon };
  await page.route('**/api/v1/sessions/current', (route) =>
    route.fulfill({
      json: { id: 'admin-demo', email: 'admin@example.test', emailVerified: true, role: 'ADMIN' },
    }),
  );
  await page.route('**/api/v1/admin/coupons**', async (route) => {
    const request = route.request();
    if (request.method() === 'GET') {
      await route.fulfill({
        json: {
          content: current.id ? [current] : [],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      });
      return;
    }
    if (request.method() === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>;
      expect(body.code).toBe('NOVO10');
      expect(body.discountValue).toBe(1250);
      current = { ...initialCoupon, ...body, code: String(body.code), active: true };
      await route.fulfill({ status: 201, json: current });
      return;
    }
    if (request.method() === 'PATCH') {
      const body = request.postDataJSON() as Record<string, unknown>;
      expect(body.code).toBeUndefined();
      expect(body.active).toBe(false);
      current = { ...current, ...body };
      await route.fulfill({ json: current });
      return;
    }
    await route.fulfill({ status: 405 });
  });

  await page.goto('/admin/coupons');
  await expect(page.getByRole('heading', { name: 'Cupons' })).toBeVisible();
  await expect(page.getByText('BEMVINDO', { exact: true })).toBeVisible();
  await expect(page.getByText(/12 de 50 usos/)).toBeVisible();

  await page.getByRole('button', { name: 'Novo cupom' }).click();
  await page.getByLabel('Código').fill(' novo10 ');
  await page.getByLabel('Tipo de desconto').selectOption('FIXED');
  await page.getByLabel('Desconto em reais').fill('12,50');
  await page.getByLabel('Subtotal mais frete mínimo (R$)').fill('75,00');
  await page.getByLabel('Limite total de usos').fill('20');
  await page.getByRole('button', { name: 'Criar cupom' }).click();
  await expect(page.locator('.success')).toContainText('Cupom criado');
  await expect(page.getByText('NOVO10', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Editar regra' }).first().click();
  await expect(page.getByLabel('Código')).toHaveAttribute('readonly', '');
  await page.getByLabel('Cupom ativo').uncheck();
  await page.getByRole('button', { name: 'Salvar alterações' }).click();
  await expect(page.locator('.success')).toContainText('Cupom desativado');
  await expect(page.getByText('Inativo', { exact: true })).toBeVisible();
});
