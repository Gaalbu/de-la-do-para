import { expect, test } from '@playwright/test';

const producer = {
  id: '2c50c447-2c60-4bf6-9207-4028685019c0',
  slug: 'cacau-teste',
  displayName: 'Coletivo Cacau de Demonstração',
  originLabel: 'Sul do Pará (demonstração)',
  description: 'Texto editorial fictício para demonstração.',
  demonstration: true,
  active: true,
  createdAt: '2026-09-21T12:00:00Z',
  updatedAt: '2026-09-21T12:00:00Z',
};

const timestamp = '2026-09-21T12:00:00Z';

test('admin creates and edits food and craft products with confirmation before deactivation', async ({
  page,
}) => {
  const products: Array<Record<string, unknown>> = [];
  let conflictOnce = true;
  let nextId = 1;

  await page.route('**/api/v1/sessions/current', async (route) => {
    await route.fulfill({
      json: { id: 'admin-demo', email: 'admin@example.test', emailVerified: true, role: 'ADMIN' },
    });
  });
  await page.route('**/api/v1/admin/producers**', async (route) => {
    const url = new URL(route.request().url());
    await route.fulfill({
      json: {
        content: url.searchParams.get('page') === '0' ? [producer] : [],
        page: Number(url.searchParams.get('page') ?? 0),
        size: 50,
        totalElements: 1,
        totalPages: 1,
      },
    });
  });
  await page.route('**/api/v1/admin/products**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === 'GET') {
      const content = products.map((item) => item);
      await route.fulfill({
        json: {
          content,
          page: Number(url.searchParams.get('page') ?? 0),
          size: 20,
          totalElements: content.length,
          totalPages: content.length === 0 ? 0 : 1,
        },
      });
      return;
    }
    if (request.method() === 'POST') {
      if (conflictOnce) {
        conflictOnce = false;
        await route.fulfill({
          status: 409,
          json: { codigo: 'CATALOG_CONFLICT', detail: 'Identificador duplicado.' },
        });
        return;
      }
      const body = request.postDataJSON() as Record<string, unknown>;
      const item = {
        ...body,
        id: `product-${nextId++}`,
        demonstration: true,
        active: true,
        skus: (body['skus'] as Array<Record<string, unknown>>).map((variant) => ({
          ...variant,
          id: variant['id'] ?? `sku-created-${nextId}`,
          createdAt: timestamp,
          updatedAt: timestamp,
        })),
        createdAt: timestamp,
        updatedAt: timestamp,
      };
      products.push(item);
      await route.fulfill({ status: 201, json: item });
      return;
    }
    if (request.method() === 'PATCH') {
      const id = url.pathname.split('/').at(-1);
      const index = products.findIndex((item) => item['id'] === id);
      if (index < 0) {
        await route.fulfill({ status: 404 });
        return;
      }
      const body = request.postDataJSON() as Record<string, unknown>;
      products[index] = {
        ...products[index],
        ...body,
        skus: (body['skus'] as Array<Record<string, unknown>>).map((variant) => ({
          ...variant,
          id: variant['id'] ?? `sku-updated-${nextId}`,
          createdAt: timestamp,
          updatedAt: timestamp,
        })),
        updatedAt: timestamp,
      };
      await route.fulfill({ json: products[index] });
      return;
    }
    await route.fulfill({ status: 405 });
  });

  await page.goto('/admin/products');
  await expect(page.getByRole('heading', { name: 'Produtos' })).toBeVisible();
  await expect(page.getByLabel('Produtor')).toContainText(producer.displayName);

  await page.getByLabel('Identificador').fill('chocolate-demo');
  await page.getByLabel('Nome de exibição').fill('Chocolate de demonstração');
  await page.getByLabel('Descrição fictícia de demonstração').fill('Descrição editorial fictícia.');
  await page.getByLabel('Código SKU').fill('CHOC-DEMO');
  await page.getByLabel('Unidade de venda').fill('barra');
  await page.getByLabel('Conteúdo líquido (g)').fill('100');
  await page.getByLabel('Validade mínima na chegada (dias)').fill('30');
  await page.getByLabel('Produtor').selectOption(producer.id);
  await expect(page.getByRole('button', { name: 'Criar produto' })).toBeEnabled();
  await page.getByRole('button', { name: 'Criar produto' }).click();
  await expect(page.getByRole('alert')).toContainText('Não foi possível salvar');

  await page.getByLabel('Identificador').fill('chocolate-demo-corrigido');
  await page.getByRole('button', { name: 'Criar produto' }).click();
  await expect(page.getByText('Chocolate de demonstração')).toBeVisible();
  await expect(page.getByText('Produto criado.', { exact: false })).toBeVisible();

  await page.getByRole('button', { name: 'Editar' }).first().click();
  await page.getByLabel('Nome de exibição').fill('Chocolate atualizado de demonstração');
  await page.getByRole('button', { name: 'Salvar alterações' }).click();
  await expect(page.getByText('Chocolate atualizado de demonstração')).toBeVisible();
  await expect(page.getByText('Produto atualizado.')).toBeVisible();

  await page.getByRole('button', { name: 'Novo produto' }).click();
  await page.getByLabel('Categoria').selectOption('CRAFT');
  await expect(page.getByLabel('Conteúdo líquido (g)')).toHaveCount(0);
  await page.getByLabel('Identificador').fill('cuia-demo');
  await page.getByLabel('Nome de exibição').fill('Cuia de demonstração');
  await page.getByLabel('Descrição fictícia de demonstração').fill('Peça editorial fictícia.');
  await page.getByLabel('Código SKU').fill('CUIA-DEMO');
  await page.getByLabel('Unidade de venda').fill('peça');
  await page.getByLabel('Item frágil').check();
  await page.getByLabel('Produtor').selectOption(producer.id);
  await page.getByRole('button', { name: 'Criar produto' }).click();
  await expect(page.getByText('Cuia de demonstração')).toBeVisible();
  await expect(page.getByText(/Artesanato · 1 variante/)).toBeVisible();

  await page.getByRole('button', { name: 'Editar' }).nth(1).click();
  await page
    .getByLabel('Descrição fictícia de demonstração')
    .fill('Peça atualizada, ainda fictícia.');
  await page.getByRole('button', { name: 'Salvar alterações' }).click();
  await expect(page.getByText('Produto atualizado.')).toBeVisible();

  page.once('dialog', async (dialog) => dialog.dismiss());
  await page.getByRole('button', { name: 'Desativar produto' }).click();
  await expect(page.getByRole('button', { name: 'Desativar produto' })).toBeVisible();
  page.once('dialog', async (dialog) => dialog.accept());
  await page.getByRole('button', { name: 'Desativar produto' }).click();
  await page.getByRole('button', { name: 'Salvar alterações' }).click();
  await expect(page.getByText('Produto desativado.', { exact: false })).toBeVisible();
  await expect(page.getByText('Inativo', { exact: true })).toHaveCount(1);
});
