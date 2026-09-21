import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './specs',
  fullyParallel: true,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'python3 -m http.server 4200 --directory ../dist/frontend/browser',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env['CI'],
    timeout: 30_000,
  },
});
