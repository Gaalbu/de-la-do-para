import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './live',
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'off',
  },
  webServer: {
    command: 'node ../dist/frontend/server/server.mjs',
    url: 'http://localhost:4200',
    reuseExistingServer: false,
    timeout: 30_000,
    env: {
      PORT: '4200',
    },
  },
});
