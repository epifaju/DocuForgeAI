import { expect, test } from "@playwright/test";
import { FIELD, seedTemplateForRun, uiLogin } from "../helpers/auth";

test.describe("Templates, form, generation (§101.4–10,13)", () => {
  test("import via API → UI form → generate document", async ({ page }) => {
    const { template } = await seedTemplateForRun();
    await uiLogin(page);

    await page.getByRole("link", { name: "Templates" }).click();
    await expect(page.getByText(template.name)).toBeVisible();
    await expect(page.getByText(template.code, { exact: false })).toBeVisible();

    const row = page.locator("li").filter({ hasText: template.name });
    await row.getByRole("link", { name: "Formulaire" }).click();
    await expect(page).toHaveURL(new RegExp(`/forms/${template.currentVersionId}`));
    await expect(page.getByText(template.name)).toBeVisible();
    await expect(page.getByText(template.code)).toBeVisible();

    // Required field empty → validation error
    await page.getByLabel(FIELD.firstName, { exact: false }).fill("");
    await page.getByLabel(FIELD.email, { exact: false }).fill("alice.e2e@example.com");
    await page.getByLabel(FIELD.total, { exact: false }).fill("42");
    await page.getByRole("button", { name: "Generer le document" }).click();
    await expect(page.getByText(/Champ obligatoire|obligatoire|invalide/i).first()).toBeVisible();

    // Valid generation
    await page.getByLabel(FIELD.firstName, { exact: false }).fill("Alice");

    const downloadPromise = page.waitForEvent("download", { timeout: 90_000 });
    await page.getByRole("button", { name: "Generer le document" }).click();
    await expect(page.getByText(/Document .+ genere/i)).toBeVisible({ timeout: 90_000 });
    const download = await downloadPromise;
    const suggested = download.suggestedFilename();
    expect(suggested.toLowerCase()).toMatch(/\.(pdf|docx)$/);

    await page.getByRole("link", { name: "Voir dans le repository" }).click();
    await expect(page).toHaveURL(/\/documents\/[0-9a-f-]+/);
    await expect(page.getByText("Statut")).toBeVisible();
    await expect(page.getByRole("heading", { name: template.name })).toBeVisible();
  });
});
