import { defineConfig, devices } from '@playwright/test'

// E2E_BASE_URL aponta os testes pro ambiente implantado (ex.: https://<host-do-nlb>); sem ela,
// sobe o vite dev server local. O certificado do ingress é autoassinado, daí ignoreHTTPSErrors.
const deployedBaseUrl = process.env.E2E_BASE_URL

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: deployedBaseUrl ?? 'http://localhost:5173',
    ignoreHTTPSErrors: deployedBaseUrl !== undefined,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: deployedBaseUrl
    ? undefined
    : {
        command: 'npm run dev',
        url: 'http://localhost:5173',
        reuseExistingServer: !process.env.CI,
        timeout: 30_000,
      },
})
