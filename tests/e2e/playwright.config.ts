import { defineConfig, devices } from "@playwright/test";

/** Prefer a dedicated Vite port so Docker's older frontend on :5174 is not reused. */
const baseURL = process.env.E2E_BASE_URL ?? "http://127.0.0.1:5175";
const vitePort = process.env.E2E_VITE_PORT ?? "5175";

export default defineConfig({
  testDir: "./specs",
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 240_000,
  expect: { timeout: 30_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${vitePort}`,
    cwd: "../../frontend",
    url: baseURL,
    // Local: reuse Vite already on :5175. CI: always start a fresh server.
    reuseExistingServer: !process.env.CI || !!process.env.E2E_REUSE_SERVER,
    timeout: 120_000,
  },
});
