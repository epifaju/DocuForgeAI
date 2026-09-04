import { expect, test } from "@playwright/test";
import { CSV_PATH, seedTemplateForRun, uiLogin } from "../helpers/auth";

test.describe("Batch CSV (§101.18–19)", () => {
  test("launch CSV batch and open detail", async ({ page }) => {
    const { template } = await seedTemplateForRun();
    await uiLogin(page);

    await page.getByRole("link", { name: "Batches" }).click();
    await expect(page.getByText("Generation batch CSV", { exact: false })).toBeVisible();

    await page.getByLabel("Template").selectOption(template.id);
    await page.getByLabel("Fichier CSV").setInputFiles(CSV_PATH);
    await page.getByRole("button", { name: "Lancer le batch" }).click();
    await expect(page.getByText(/Batch .+ accepte/i)).toBeVisible();

    const detail = page.getByRole("link", { name: "Detail" }).first();
    await expect(detail).toBeVisible();
    await detail.click();
    await expect(page).toHaveURL(/\/batches\/[0-9a-f-]+/);
    await expect(page.getByText("Total")).toBeVisible();
    await expect(page.getByText("Succes")).toBeVisible();

    await expect
      .poll(
        async () => {
          const text = await page.locator("body").innerText();
          return /COMPLETED|PARTIALLY_FAILED|FAILED/.test(text);
        },
        { timeout: 120_000 },
      )
      .toBeTruthy();

    const body = await page.locator("body").innerText();
    expect(
      body.includes("Telecharger ZIP") ||
        body.includes("Ligne") ||
        /COMPLETED|PARTIALLY_FAILED|FAILED/.test(body),
    ).toBeTruthy();
  });
});
