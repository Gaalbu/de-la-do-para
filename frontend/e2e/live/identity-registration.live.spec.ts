import { expect, test } from '@playwright/test';

const apiBaseUrl = process.env['C81_API_BASE_URL'];
const mailpitApiUrl = process.env['C81_MAILPIT_API_URL'];

test('registers through the real API, reads Mailpit and verifies the account', async ({ page }) => {
  if (!apiBaseUrl || !mailpitApiUrl) {
    throw new Error(
      'Set C81_API_BASE_URL and C81_MAILPIT_API_URL to run this live integration test.',
    );
  }

  const email = `c81-${crypto.randomUUID()}@example.com`;
  const password = `C81-test-${crypto.randomUUID()}`;

  await page.route('**/api/v1/**', (route) => {
    const requestUrl = new URL(route.request().url());
    return route
      .fetch({ url: `${apiBaseUrl}${requestUrl.pathname}${requestUrl.search}` })
      .then((response) => route.fulfill({ response }));
  });

  let registrationStatus: number | undefined;
  page.on('response', (response) => {
    if (
      new URL(response.url()).pathname === '/api/v1/accounts' &&
      response.request().method() === 'POST'
    ) {
      registrationStatus = response.status();
    }
  });

  await page.goto('/login');
  await page.getByRole('link', { name: 'Crie uma conta opcional' }).click();
  await expect(page).toHaveURL(/\/register$/);
  await expect(page.getByText('Você continua podendo comprar como convidado.')).toBeVisible();
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(password);
  await page.getByRole('button', { name: 'Criar conta' }).click();
  await expect(page.getByRole('heading', { name: 'Cadastro recebido' })).toBeVisible();
  expect(registrationStatus).toBe(201);

  const verificationMessage = await waitForMailMessage(mailpitApiUrl, email, 'Confirme seu e-mail');
  expect(verificationMessage.subject).toContain('Confirme seu e-mail');
  const token = verificationMessage.text.match(/\/verify-email#token=([A-Za-z0-9_-]+)/)?.[1];
  expect(token).toBeTruthy();

  await page.goto(`/verify-email#token=${token}`);
  await expect(page.getByText('E-mail confirmado. Sua conta está pronta.')).toBeVisible();
  await expect(page).toHaveURL(/\/verify-email$/);

  await page.goto('/login');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(password);
  const loginResponsePromise = page.waitForResponse((response) => {
    const request = response.request();
    return new URL(response.url()).pathname === '/api/v1/sessions' && request.method() === 'POST';
  });
  await page.getByRole('button', { name: 'Entrar' }).click();
  const loginResponse = await loginResponsePromise;
  expect(loginResponse.status()).toBe(200);
  await expect(loginResponse.json()).resolves.toMatchObject({
    role: 'CUSTOMER',
    emailVerified: true,
    cartMergeRequired: false,
  });
  await expect(page).toHaveURL('/');
  await page.unrouteAll({ behavior: 'ignoreErrors' });
});

test('recovers access through the real API and Mailpit, then rejects the reused link', async ({
  page,
}) => {
  if (!apiBaseUrl || !mailpitApiUrl) {
    throw new Error(
      'Set C81_API_BASE_URL and C81_MAILPIT_API_URL to run this live integration test.',
    );
  }

  const email = `c81-recovery-${crypto.randomUUID()}@example.com`;
  const password = `C81-test-${crypto.randomUUID()}`;
  const newPassword = `C81-reset-${crypto.randomUUID()}`;

  await page.route('**/api/v1/**', (route) => {
    const requestUrl = new URL(route.request().url());
    return route
      .fetch({ url: `${apiBaseUrl}${requestUrl.pathname}${requestUrl.search}` })
      .then((response) => route.fulfill({ response }));
  });

  await page.goto('/register');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(password);
  await page.getByRole('button', { name: 'Criar conta' }).click();
  await expect(page.getByRole('heading', { name: 'Cadastro recebido' })).toBeVisible();

  const verificationMessage = await waitForMailMessage(mailpitApiUrl, email, 'Confirme seu e-mail');
  const verificationToken = verificationMessage.text.match(
    /\/verify-email#token=([A-Za-z0-9_-]+)/,
  )?.[1];
  expect(verificationToken).toBeTruthy();
  await page.goto(`/verify-email#token=${verificationToken}`);
  await expect(page.getByText('E-mail confirmado. Sua conta está pronta.')).toBeVisible();

  await page.goto('/recover-access');
  await page.getByLabel('E-mail').fill(email);
  const recoveryResponsePromise = page.waitForResponse((response) => {
    const request = response.request();
    return (
      new URL(response.url()).pathname === '/api/v1/accounts/recovery' &&
      request.method() === 'POST'
    );
  });
  await page.getByRole('button', { name: 'Enviar instruções' }).click();
  const recoveryResponse = await recoveryResponsePromise;
  expect(recoveryResponse.status()).toBe(202);
  await expect(page.getByRole('status')).toContainText('Se houver uma conta verificada');

  const recoveryMessage = await waitForMailMessage(mailpitApiUrl, email, 'Redefina sua senha');
  const recoveryToken = recoveryMessage.text.match(/\/reset-password#token=([A-Za-z0-9_-]+)/)?.[1];
  expect(recoveryToken).toBeTruthy();
  await page.goto(`/reset-password#token=${recoveryToken}`);
  await expect(page).toHaveURL(/\/reset-password$/);
  await page.getByLabel('Nova senha').fill(newPassword);
  const resetResponsePromise = page.waitForResponse((response) => {
    const request = response.request();
    return (
      new URL(response.url()).pathname === '/api/v1/accounts/reset' && request.method() === 'POST'
    );
  });
  await page.getByRole('button', { name: 'Atualizar senha' }).click();
  const resetResponse = await resetResponsePromise;
  expect(resetResponse.status()).toBe(200);
  await expect(resetResponse.json()).resolves.toMatchObject({ passwordChanged: true });
  await expect(page.getByRole('status')).toContainText('Senha atualizada');
  await expect(page).toHaveURL(/\/reset-password$/);

  await page.goto('/login');
  await page.goto(`/reset-password#token=${recoveryToken}`);
  await page.getByLabel('Nova senha').fill(`C81-reused-${crypto.randomUUID()}`);
  const reusedTokenResponsePromise = page.waitForResponse((response) => {
    const request = response.request();
    return (
      new URL(response.url()).pathname === '/api/v1/accounts/reset' && request.method() === 'POST'
    );
  });
  await page.getByRole('button', { name: 'Atualizar senha' }).click();
  const reusedTokenResponse = await reusedTokenResponsePromise;
  expect(reusedTokenResponse.status()).toBe(410);
  await expect(page.getByRole('alert')).toContainText('Solicite outro');
  await page.goto('/login');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(newPassword);
  const loginResponsePromise = page.waitForResponse((response) => {
    const request = response.request();
    return new URL(response.url()).pathname === '/api/v1/sessions' && request.method() === 'POST';
  });
  await page.getByRole('button', { name: 'Entrar' }).click();
  const loginResponse = await loginResponsePromise;
  expect(loginResponse.status()).toBe(200);
  await expect(loginResponse.json()).resolves.toMatchObject({
    role: 'CUSTOMER',
    emailVerified: true,
  });
  await expect(page).toHaveURL('/');
  await page.unrouteAll({ behavior: 'ignoreErrors' });
});

async function waitForMailMessage(
  mailpitBaseUrl: string,
  email: string,
  subject: string,
): Promise<{ subject: string; text: string }> {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const response = await fetch(`${mailpitBaseUrl}/api/v1/messages?limit=100`);
    if (!response.ok) {
      throw new Error(`Mailpit message listing failed with HTTP ${response.status}.`);
    }
    const listing = (await response.json()) as {
      messages: Array<{ ID: string; Subject: string; To: Array<{ Address: string }> }>;
    };
    const message = listing.messages.find(
      (candidate) =>
        candidate.Subject.includes(subject) &&
        candidate.To.some((recipient) => recipient.Address.toLowerCase() === email.toLowerCase()),
    );
    if (message) {
      const detailResponse = await fetch(`${mailpitBaseUrl}/api/v1/message/${message.ID}`);
      if (!detailResponse.ok) {
        throw new Error(`Mailpit message lookup failed with HTTP ${detailResponse.status}.`);
      }
      const detail = (await detailResponse.json()) as { Subject: string; Text: string };
      return { subject: detail.Subject, text: detail.Text };
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error('Timed out waiting for the verification email in Mailpit.');
}
