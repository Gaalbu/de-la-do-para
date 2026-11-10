import { expect, test } from '@playwright/test';

// ADR-0004 / C85: private pages must never be cacheable by a shared cache and
// must not echo the session cookie into server-rendered HTML.
const privateRoutes = ['/cart', '/checkout', '/orders/abc', '/login', '/admin', '/admin/products'];

for (const route of privateRoutes) {
  test(`${route} is not cacheable and does not leak the session cookie`, async ({ request }) => {
    const response = await request.get(route, { headers: { cookie: 'SESSION=secret-session' } });
    expect(response.headers()['cache-control']).toMatch(/no-store/);
    expect(response.headers()['cache-control']).toMatch(/private/);
    expect(await response.text()).not.toContain('secret-session');
  });
}

test('public pages are not forced to no-store', async ({ request }) => {
  const response = await request.get('/');
  expect(response.headers()['cache-control'] ?? '').not.toMatch(/no-store/);
});
