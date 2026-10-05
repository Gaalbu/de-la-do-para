import { expect, test } from '@playwright/test';

test('recovery gives the same response for every email', async ({ page }) => {
  await page.route('**/api/v1/csrf', (route) =>
    route.fulfill({
      json: { token: 'csrf-token' },
      headers: { 'set-cookie': 'XSRF-TOKEN=csrf-token; Path=/' },
    }),
  );
  let recoveryCount = 0;
  await page.route('**/api/v1/accounts/recovery', async (route) => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().headers()['x-xsrf-token']).toBe('csrf-token');
    expect(route.request().postDataJSON()).toEqual({ email: 'unknown@example.com' });
    recoveryCount += 1;
    await route.fulfill({ status: 202 });
  });

  await page.goto('/login');
  await page.getByRole('link', { name: 'Esqueci minha senha' }).click();
  await expect(page).toHaveURL(/\/recover-access$/);
  await page.getByLabel('E-mail').fill('unknown@example.com');
  await page.getByRole('button', { name: 'Enviar instruções' }).click();
  await expect(page.getByRole('status')).toContainText('Se houver uma conta verificada');
  expect(recoveryCount).toBe(1);
});

test('reset consumes the fragment token and allows requesting another link after expiry', async ({
  page,
}) => {
  await page.route('**/api/v1/csrf', (route) =>
    route.fulfill({
      json: { token: 'csrf-token' },
      headers: { 'set-cookie': 'XSRF-TOKEN=csrf-token; Path=/' },
    }),
  );
  let resetBody: unknown;
  await page.route('**/api/v1/accounts/reset', async (route) => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().headers()['x-xsrf-token']).toBe('csrf-token');
    resetBody = route.request().postDataJSON();
    await route.fulfill({ status: 410, json: { title: 'Token expirado', status: 410 } });
  });

  await page.goto('/reset-password#token=expired-or-reused-token');
  await expect(page).toHaveURL(/\/reset-password$/);
  await page.getByLabel('Nova senha').fill('nova-senha-segura');
  await page.getByRole('button', { name: 'Atualizar senha' }).click();
  await expect(page.getByRole('alert')).toContainText('Solicite outro');
  expect(resetBody).toEqual({
    token: 'expired-or-reused-token',
    newPassword: 'nova-senha-segura',
  });
  await page.getByRole('link', { name: 'Solicitar outro link' }).click();
  await expect(page).toHaveURL(/\/recover-access$/);
});

test('successful password reset clears the token and links back to login', async ({ page }) => {
  await page.route('**/api/v1/csrf', (route) =>
    route.fulfill({
      json: { token: 'csrf-token' },
      headers: { 'set-cookie': 'XSRF-TOKEN=csrf-token; Path=/' },
    }),
  );
  await page.route('**/api/v1/accounts/reset', async (route) => {
    expect(route.request().postDataJSON()).toEqual({
      token: 'one-time-token',
      newPassword: 'senha-nova-segura',
    });
    await route.fulfill({ json: { passwordChanged: true } });
  });

  await page.goto('/reset-password#token=one-time-token');
  await page.getByLabel('Nova senha').fill('senha-nova-segura');
  await page.getByRole('button', { name: 'Atualizar senha' }).click();
  await expect(page.getByRole('status')).toContainText('Senha atualizada');
  await expect(page).toHaveURL(/\/reset-password$/);
  await page
    .getByRole('main', { name: 'Redefinir senha' })
    .getByRole('link', { name: 'Entrar' })
    .click();
  await expect(page).toHaveURL(/\/login$/);
});
