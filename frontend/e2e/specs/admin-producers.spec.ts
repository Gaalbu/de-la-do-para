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

test('admin manages demo producers without physical deletion', async ({ page }) => {
  let current = { ...producer };
  const listProducers = async (pageNumber: number) => ({
    content: pageNumber === 0 ? [current] : [],
    page: pageNumber,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  });

  await page.route('**/api/v1/sessions/current', async (route) => {
    await route.fulfill({
      json: { id: 'admin-demo', email: 'admin@example.test', emailVerified: true, role: 'ADMIN' },
    });
  });
  await page.route('**/api/v1/admin/producers**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === 'GET') {
      await route.fulfill({
        json: await listProducers(Number(url.searchParams.get('page') ?? '0')),
      });
      return;
    }
    if (request.method() === 'POST') {
      const body = request.postDataJSON() as Omit<
        typeof producer,
        'id' | 'demonstration' | 'active' | 'createdAt' | 'updatedAt'
      >;
      current = { ...current, ...body, demonstration: true, active: true };
      await route.fulfill({ status: 201, json: current });
      return;
    }
    if (request.method() === 'PATCH') {
      current = { ...current, ...(request.postDataJSON() as Partial<typeof producer>) };
      await route.fulfill({ json: current });
      return;
    }
    await route.fulfill({ status: 405 });
  });

  await page.goto('/');
  await expect(page.getByText('admin@example.test (ADMIN)')).toBeVisible();
  await page.getByRole('link', { name: 'Admin' }).click();
  await expect(page.getByRole('heading', { name: 'Produtores' })).toBeVisible();
  await expect(page.getByText('Conteúdo de demonstração')).toBeVisible();
  await expect(page.getByText('Sul do Pará (demonstração)')).toBeVisible();
  await expect(page.getByText('Demonstração', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /excluir|apagar/i })).toHaveCount(0);

  await page.getByRole('button', { name: 'Novo produtor' }).click();
  await page.getByLabel('Identificador').fill('cacau-teste');
  await page.getByLabel('Nome de exibição').fill('Coletivo Cacau de Demonstração');
  await page.getByLabel('Localidade ampla fictícia').fill('Sul do Pará (demonstração)');
  await page
    .getByLabel('Texto editorial fictício')
    .fill('Texto editorial fictício para demonstração.');
  await expect(page.getByRole('button', { name: 'Criar produtor' })).toBeEnabled();
  await page.getByRole('button', { name: 'Criar produtor' }).click();
  await expect(page.getByText(/Produtor criado/)).toBeVisible();
  await expect(page.getByText('Coletivo Cacau de Demonstração')).toBeVisible();

  await page.getByRole('button', { name: 'Editar' }).click();
  await page.getByLabel('Nome de exibição').fill('Coletivo Cacau Atualizado (demonstração)');
  await page.getByLabel('Produtor ativo').uncheck();
  await page.getByRole('button', { name: 'Salvar alterações' }).click();
  await expect(page.getByText(/Produtor desativado/)).toBeVisible();
  await expect(page.getByText('Inativo', { exact: true })).toBeVisible();
  await expect(page.getByText('Coletivo Cacau Atualizado (demonstração)')).toBeVisible();

  await page.reload();
  await expect(page.getByText('Coletivo Cacau Atualizado (demonstração)')).toBeVisible();
  await expect(page.getByText('Inativo', { exact: true })).toBeVisible();
});
