import { expect, test } from '@playwright/test';

test('registration is optional and verification consumes a fragment token', async ({ page }) => {
  await page.route('**/api/v1/csrf', (route) =>
    route.fulfill({
      json: { token: 'csrf-token' },
      headers: { 'set-cookie': 'XSRF-TOKEN=csrf-token; Path=/' },
    }),
  );
  await page.route('**/api/v1/accounts', async (route) => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().headers()['x-xsrf-token']).toBe('csrf-token');
    expect(route.request().postDataJSON()).toEqual({
      email: 'ana@example.com',
      password: 'correct-horse',
    });
    await route.fulfill({
      status: 201,
      json: {
        id: 'account-1',
        email: 'ana@example.com',
        emailVerified: false,
        role: 'CUSTOMER',
        cartMergeRequired: false,
      },
    });
  });

  await page.goto('/login');
  await page.getByRole('link', { name: 'Crie uma conta opcional' }).click();
  await expect(page).toHaveURL(/\/register$/);
  await expect(page.getByText('Você continua podendo comprar como convidado.')).toBeVisible();
  await page.getByLabel('E-mail').fill('ana@example.com');
  await page.getByLabel('Senha').fill('correct-horse');
  await page.getByRole('button', { name: 'Criar conta' }).click();
  await expect(page.getByRole('heading', { name: 'Cadastro recebido' })).toBeVisible();

  let verificationBody: unknown;
  await page.route('**/api/v1/accounts/verify', async (route) => {
    verificationBody = route.request().postDataJSON();
    await route.fulfill({ json: { emailVerified: true } });
  });
  await page.goto('/verify-email#token=single-use-token');
  await expect(page.getByText('E-mail confirmado. Sua conta está pronta.')).toBeVisible();
  await expect(page).toHaveURL(/\/verify-email$/);
  expect(verificationBody).toEqual({ token: 'single-use-token' });
});

test('verification failure does not leave the token in the address bar', async ({ page }) => {
  await page.route('**/api/v1/accounts/verify', (route) =>
    route.fulfill({ status: 410, json: { title: 'Token expirado', status: 410 } }),
  );
  await page.goto('/verify-email#token=expired-token');
  await expect(page.getByRole('alert')).toContainText('O link expirou');
  await expect(page).toHaveURL(/\/verify-email$/);
  await expect(
    page.getByText('Sua conta continua opcional para comprar como convidado.'),
  ).toBeVisible();
});
