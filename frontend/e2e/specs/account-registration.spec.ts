import { expect, test } from '@playwright/test';

test('registers an optional customer and verifies email without retaining the token in the URL', async ({
  page,
}) => {
  await page.route('**/api/v1/csrf', (route) => route.fulfill({ json: { token: 'csrf-token' } }));
  await page.route('**/api/v1/accounts', async (route) => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().postDataJSON()).toEqual({
      email: 'ana@example.com',
      password: 'senha-segura-123',
    });
    await route.fulfill({
      status: 201,
      json: {
        id: 'customer-1',
        email: 'ana@example.com',
        emailVerified: false,
        role: 'CUSTOMER',
      },
    });
  });

  let urlAtVerificationPost = '';
  await page.route('**/api/v1/accounts/verify', async (route) => {
    urlAtVerificationPost = page.url();
    expect(route.request().postDataJSON()).toEqual({ token: 'opaque-verification-token' });
    await route.fulfill({
      json: {
        id: 'customer-1',
        email: 'ana@example.com',
        emailVerified: true,
        role: 'CUSTOMER',
      },
    });
  });

  await page.goto('/register');
  await page.getByLabel('E-mail').fill('ana@example.com');
  await page.getByLabel('Senha').fill('senha-segura-123');
  await page.getByRole('button', { name: 'Criar conta' }).click();
  await expect(page.getByRole('heading', { name: 'Confira seu e-mail' })).toBeVisible();

  await page.goto('/verify-email#token=opaque-verification-token');
  await expect(page.getByText('E-mail confirmado. Sua conta já pode ser usada.')).toBeVisible();
  expect(urlAtVerificationPost).toBe('http://localhost:4200/verify-email');
  expect(page.url()).toBe('http://localhost:4200/verify-email');
});
