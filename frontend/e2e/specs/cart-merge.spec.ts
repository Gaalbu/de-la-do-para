import { expect, test } from '@playwright/test';

test('login with two tabs asks before combining guest and account carts', async ({
  context,
  page,
}) => {
  let mergedChoice: unknown;
  let cartMode: 'guest' | 'account' = 'guest';
  const guestTab = await context.newPage();
  await context.route('**/api/v1/cart', (route) => {
    if (cartMode === 'guest') {
      return route.fulfill({
        json: { id: 'guest-cart', version: 1, items: [{ skuId: 'active-sku', quantity: 4 }] },
      });
    }
    return route.fulfill({
      json: {
        id: 'account-cart',
        version: 4,
        items: [
          { skuId: 'active-sku', quantity: 7 },
          { skuId: 'inactive-sku', quantity: 2 },
        ],
      },
    });
  });
  await guestTab.goto('/cart');
  await expect(guestTab.getByRole('heading', { name: 'Seu carrinho' })).toBeVisible();

  await page.route('**/api/v1/csrf', (route) => route.fulfill({ json: { token: 'csrf-token' } }));
  await page.route('**/api/v1/sessions', async (route) => {
    expect(route.request().method()).toBe('POST');
    await route.fulfill({
      json: {
        id: 'customer-1',
        email: 'ana@example.com',
        emailVerified: true,
        role: 'CUSTOMER',
        cartMergeRequired: true,
      },
    });
  });
  await page.route('**/api/v1/cart/merge', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        json: {
          inactiveSkuIds: ['inactive-sku'],
          accountCart: {
            id: 'account-cart',
            version: 3,
            items: [{ skuId: 'active-sku', quantity: 3 }],
          },
          guestCart: {
            id: 'guest-cart',
            version: 2,
            items: [
              { skuId: 'active-sku', quantity: 4 },
              { skuId: 'inactive-sku', quantity: 2 },
            ],
          },
        },
      });
      return;
    }
    mergedChoice = route.request().postDataJSON();
    cartMode = 'account';
    await route.fulfill({
      json: {
        id: 'account-cart',
        version: 4,
        items: [
          { skuId: 'active-sku', quantity: 7 },
          { skuId: 'inactive-sku', quantity: 2 },
        ],
      },
    });
  });
  await page.goto('/login');
  await page.getByLabel('E-mail').fill('ana@example.com');
  await page.getByLabel('Senha').fill('correct-horse');
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page).toHaveURL(/\/cart\/merge$/);
  await expect(
    page.getByRole('heading', { name: 'Escolha como organizar seus carrinhos' }),
  ).toBeVisible();
  await expect(page.getByText('Item inactive-sku — quantidade 2')).toBeVisible();
  await expect(
    page.getByText('Indisponível no catálogo; será revalidado no checkout.'),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Combinar quantidades por item' }).click();

  await expect(page).toHaveURL(/\/cart$/);
  await expect(page.getByText('7')).toBeVisible();
  expect(mergedChoice).toEqual({
    choice: 'COMBINE',
    expectedAccountVersion: 3,
    expectedGuestVersion: 2,
  });
});
