import { defineConfig, devices } from '@playwright/test'

/**
 * Browser end-to-end tests against a running stack — nothing is started here.
 *   E2E_BASE_URL     the SPA with /api on the same origin (default: the minikube gateway)
 *   E2E_MAILPIT_URL  Mailpit, to follow the links the backend mails
 */
export default defineConfig({
  testDir: './tests',
  // One worker: the tests share the stack's per-IP auth rate limits and its mailbox.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:30080',
    locale: 'ru-RU',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
