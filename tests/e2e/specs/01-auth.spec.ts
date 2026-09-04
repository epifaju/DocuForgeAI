import { expect, test } from "@playwright/test";
import { CREDENTIALS } from "../helpers/api";
import { uiLogin } from "../helpers/auth";

test.describe("Auth & access (§101.2, §101.22)", () => {
  test("login success lands on dashboard", async ({ page }) => {
    await uiLogin(page);
    await expect(page.getByText("Tableau de bord", { exact: false })).toBeVisible();
    await expect(page.getByRole("link", { name: "Dashboard" })).toBeVisible();
    await expect(page.getByText("Documents aujourd'hui")).toBeVisible();
  });

  test("bad password stays on login with error", async ({ page }) => {
    await page.goto("/login");
    await page.getByLabel("Societe").fill(CREDENTIALS.companyIdentifier);
    await page.getByLabel("Email").fill(CREDENTIALS.email);
    await page.getByLabel("Mot de passe").fill("wrong-password-not-valid");
    await page.getByRole("button", { name: "Se connecter" }).click();
    await expect(page).toHaveURL(/\/login/);
    await expect(page.locator("p.text-\\[var\\(--danger\\)\\], p").filter({ hasText: /.+/ }).first()).toBeVisible();
  });

  test("unauthenticated visit redirects to login", async ({ page }) => {
    await page.goto("/documents");
    await page.waitForURL("**/login");
  });
});
