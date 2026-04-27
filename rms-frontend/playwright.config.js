import { defineConfig, devices } from "@playwright/test";

const port = Number(process.env.RMS_E2E_PORT || 4173);
const baseURL = process.env.RMS_E2E_BASE_URL || `http://localhost:${port}`;
const apiBaseUrl = process.env.RMS_E2E_API_BASE_URL || "http://localhost:8081/api";
const frontendApiBaseUrl = apiBaseUrl.replace(/\/api\/?$/, "");

export default defineConfig({
  testDir: "./e2e",
  globalTeardown: "./e2e/global-teardown.js",
  timeout: 45_000,
  expect: {
    timeout: 10_000,
  },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  webServer: {
    command: `npm run dev -- --host localhost --port ${port}`,
    url: baseURL,
    reuseExistingServer: true,
    timeout: 120_000,
    env: {
      ...process.env,
      VITE_API_BASE_URL: process.env.VITE_API_BASE_URL || frontendApiBaseUrl,
    },
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
