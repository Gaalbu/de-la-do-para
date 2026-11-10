import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

// WCAG 2.1 A/AA automatic scan of the routes reachable without data (C86).
// Keyboard journeys and contrast on real content still need manual review.
const routes = ['/', '/login', '/cart', '/checkout', '/admin'];

for (const route of routes) {
  test(`no automatic WCAG A/AA violations on ${route}`, async ({ page }) => {
    await page.route('**/api/**', (r) =>
      r.fulfill({ status: 200, json: { content: [], items: [], options: [] } }),
    );
    await page.goto(route);
    await page.locator('h1, h2').first().waitFor();
    const { violations } = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(violations.map((v) => `${v.id}: ${v.nodes.length} node(s) — ${v.help}`)).toEqual([]);
  });
}
