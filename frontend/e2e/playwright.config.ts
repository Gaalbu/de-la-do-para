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
    command: 'PORT=4200 node ../dist/frontend/server/server.mjs',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env['CI'],
    timeout: 30_000,
  },
});
