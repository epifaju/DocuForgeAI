import { expect, test, type Page } from "@playwright/test";
import { loginApi } from "../helpers/api";
import { gotoAuthed, uiLogin } from "../helpers/auth";
import {
  ARTISAN_PACK_KEY,
  ARTISAN_ZIP_FIXTURE,
  ensureArtisanPackUninstalled,
  ensureArtisanZipFixtures,
  listTemplateByCode,
} from "../helpers/packs";

const DEVIS_FIELDS: Array<{ label: RegExp; value: string }> = [
  { label: /Nom de l['']entreprise/i, value: "Atelier Demo SARL" },
  { label: /Adresse de l['']entreprise/i, value: "12 rue Fictive, 75000 Paris" },
  { label: /Nom du client/i, value: "Client Demo Martin" },
  { label: /Adresse du client/i, value: "5 avenue Exemple, 69000 Lyon" },
  { label: /Reference devis/i, value: "DEV-E2E-001" },
  { label: /Date du devis/i, value: "2026-09-01" },
  { label: /Valable jusqu/i, value: "2026-09-30" },
  { label: /Description des travaux/i, value: "Travaux fictifs de demonstration E2E" },
  { label: /Sous-total HT/i, value: "100" },
  { label: /TVA/i, value: "20" },
  { label: /Total TTC/i, value: "120" },
];

async function fillArtisanDevisForm(page: Page): Promise<void> {
  for (const field of DEVIS_FIELDS) {
    await page.getByLabel(field.label).fill(field.value);
  }
}

async function openArtisanDevisForm(page: Page, token: string): Promise<void> {
  // Prefer Templates UI (§188). Paginate if needed; fall back to API version id.
  await page.getByRole("navigation").getByRole("link", { name: "Templates", exact: true }).click();
  await expect(page).toHaveURL(/\/templates/);

  for (let attempt = 0; attempt < 15; attempt++) {
    const row = page.locator("tr").filter({ hasText: "ARTISAN_DEVIS" });
    if ((await row.count()) > 0) {
      await row.getByRole("link", { name: "Formulaire" }).click();
      await expect(page).toHaveURL(/\/forms\/[0-9a-f-]+/);
      return;
    }
    const next = page.getByRole("button", { name: "Suivant" });
    if ((await next.count()) === 0 || (await next.isDisabled())) break;
    await next.click();
    await page.waitForTimeout(300);
  }

  const tpl = await listTemplateByCode(token, "ARTISAN_DEVIS");
  if (!tpl) throw new Error("ARTISAN_DEVIS not found after pack install");
  await gotoAuthed(page, `/forms/${tpl.currentVersionId}`);
  await expect(page.getByText(/Devis Artisan|ARTISAN_DEVIS/i).first()).toBeVisible();
}

test.describe("Business Pack E2E principal (§188 / Phase 23)", () => {
  test.beforeAll(() => {
    ensureArtisanZipFixtures();
  });

  test("login → import artisan-demo → install → generate ARTISAN_DEVIS", async ({ page }) => {
    const { accessToken } = await loginApi();
    await ensureArtisanPackUninstalled(accessToken);

    await uiLogin(page);
    await expect(page.getByRole("link", { name: "Packs metier" })).toBeVisible();

    await page.getByRole("link", { name: "Packs metier" }).click();
    await expect(page).toHaveURL(/\/business-packs/);
    await expect(page.getByRole("heading", { name: "Packs metier" })).toBeVisible();

    await page.getByRole("link", { name: "Importer un pack" }).click();
    await expect(page).toHaveURL(/\/business-packs\/import$/);

    await page.locator('input[type="file"]').setInputFiles(ARTISAN_ZIP_FIXTURE);
    await expect(page.getByText(/docuforge-pack-artisan-demo-1\.0\.0\.zip/i)).toBeVisible();
    await page.getByRole("button", { name: "Envoyer" }).click();

    await expect(page).toHaveURL(/\/business-packs\/import\/[0-9a-f-]+/, { timeout: 60_000 });
    await expect(page.getByText(ARTISAN_PACK_KEY)).toBeVisible({ timeout: 90_000 });
    await expect(page.getByText(/Pack valide et pret a installer|pret a installer/i)).toBeVisible({
      timeout: 90_000,
    });

    await page.getByRole("button", { name: "Installer le pack" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Installer le pack" }).click();

    await expect(page.getByText(/Pack installe/i)).toBeVisible({ timeout: 90_000 });
    await expect(page.getByText(ARTISAN_PACK_KEY)).toBeVisible();

    await page.getByRole("link", { name: "Retour a la liste" }).click();
    await expect(page).toHaveURL(/\/business-packs$/);
    await expect(page.getByText(/Artisan Demo|artisan-demo/i).first()).toBeVisible();

    await openArtisanDevisForm(page, accessToken);
    await fillArtisanDevisForm(page);

    const downloadPromise = page.waitForEvent("download", { timeout: 120_000 });
    await page.getByRole("button", { name: "Generer le document" }).click();
    await expect(page.getByText(/Document .+ genere/i)).toBeVisible({ timeout: 120_000 });
    const download = await downloadPromise;
    expect(download.suggestedFilename().toLowerCase()).toMatch(/\.(pdf|docx)$/);

    await page.getByRole("link", { name: /Voir le document|Voir dans le repository/i }).click();
    await expect(page).toHaveURL(/\/documents\/[0-9a-f-]+/);
    await expect(page.getByRole("heading", { name: /Devis Artisan/i })).toBeVisible();
    await expect(page.getByText("COMPLETED")).toBeVisible();
  });
});
