import { expect, test, type Page } from "@playwright/test";
import { loginApi } from "../helpers/api";
import { gotoAuthed, uiLogin } from "../helpers/auth";
import {
  ARTISAN_PACK_KEY,
  ARTISAN_ZIP_FIXTURE,
  ensureArtisanPackUninstalled,
  ensureArtisanZipFixtures,
  listTemplateByCode,
  materializeArtisanPackV110,
  nextFreeArtisanMinorVersion,
} from "../helpers/packs";

const DEVIS_FIELDS: Array<{ label: RegExp; value: string }> = [
  { label: /Nom de l['']entreprise/i, value: "Atelier Demo SARL" },
  { label: /Adresse de l['']entreprise/i, value: "12 rue Fictive, 75000 Paris" },
  { label: /Nom du client/i, value: "Client Demo Martin" },
  { label: /Adresse du client/i, value: "5 avenue Exemple, 69000 Lyon" },
  { label: /Reference devis/i, value: "DEV-E2E-UPD-001" },
  { label: /Date du devis/i, value: "2026-09-01" },
  { label: /Valable jusqu/i, value: "2026-09-30" },
  { label: /Description des travaux/i, value: "Travaux fictifs avant mise a jour" },
  { label: /Sous-total HT/i, value: "100" },
  { label: /TVA/i, value: "20" },
  { label: /Total TTC/i, value: "120" },
];

async function fillDevis(page: Page, reference: string): Promise<void> {
  for (const field of DEVIS_FIELDS) {
    const value = /Reference devis/i.test(field.label.source)
      ? reference
      : field.value;
    await page.getByLabel(field.label).fill(value);
  }
}

async function importAndInstallZip(
  page: Page,
  zipPath: string,
  expectUpdate: boolean,
  updateVersion?: string,
): Promise<void> {
  // Prefer SPA navigation so the in-memory access token is preserved.
  await page.getByRole("link", { name: "Packs metier" }).click();
  await expect(page).toHaveURL(/\/business-packs/);
  await page.getByRole("link", { name: "Importer un pack" }).click();
  await expect(page.getByRole("heading", { name: /Importer un pack/i })).toBeVisible({
    timeout: 30_000,
  });
  await page.locator('input[type="file"]').setInputFiles(zipPath);
  await page.getByRole("button", { name: "Envoyer" }).click();
  await expect(page).toHaveURL(/\/business-packs\/import\/[0-9a-f-]+/, { timeout: 60_000 });
  await expect(page.getByText(ARTISAN_PACK_KEY)).toBeVisible({ timeout: 90_000 });

  if (expectUpdate) {
    await expect(page.getByRole("button", { name: "Mettre a jour" })).toBeVisible({
      timeout: 90_000,
    });
    if (updateVersion) {
      await expect(page.getByText(updateVersion).first()).toBeVisible();
    }
    await expect(page.getByText(/Nouvelle version detectee|→/i).first()).toBeVisible();
    await page.getByRole("button", { name: "Mettre a jour" }).click();
    await page.getByRole("dialog").getByRole("button", { name: /Mettre a jour/i }).click();
    await expect(page.getByText(/Pack mis a jour|Pack installe/i)).toBeVisible({ timeout: 90_000 });
  } else {
    await expect(page.getByText(/Pack valide et pret a installer|pret a installer/i)).toBeVisible({
      timeout: 90_000,
    });
    await page.getByRole("button", { name: "Installer le pack" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Installer le pack" }).click();
    await expect(page.getByText(/Pack installe/i)).toBeVisible({ timeout: 90_000 });
  }
}

test.describe("Business Pack E2E update (§189 / Phase 23)", () => {
  test.beforeAll(() => {
    ensureArtisanZipFixtures();
  });

  test("install 1.0 → generate → update minor → generate → reopen old doc", async ({ page }) => {
    const { accessToken } = await loginApi();
    await ensureArtisanPackUninstalled(accessToken);
    await uiLogin(page);

    await importAndInstallZip(page, ARTISAN_ZIP_FIXTURE, false);

    const tpl = await listTemplateByCode(accessToken, "ARTISAN_DEVIS");
    expect(tpl).not.toBeNull();
    await gotoAuthed(page, `/forms/${tpl!.currentVersionId}`);
    await fillDevis(page, "DEV-E2E-BEFORE");
    await page.getByRole("button", { name: "Generer le document" }).click();
    await expect(page.getByText(/Document .+ genere/i)).toBeVisible({ timeout: 120_000 });
    await page.getByRole("link", { name: /Voir le document|Voir dans le repository/i }).click();
    await expect(page).toHaveURL(/\/documents\/([0-9a-f-]+)/);
    const oldDocUrl = page.url();
    const oldDocId = oldDocUrl.match(/\/documents\/([0-9a-f-]+)/)?.[1];
    expect(oldDocId).toBeTruthy();

    const nextVersion = await nextFreeArtisanMinorVersion(accessToken);
    const updatePack = await materializeArtisanPackV110(nextVersion);
    await importAndInstallZip(page, updatePack.path, true, updatePack.version);
    await expect(page.getByText(updatePack.version)).toBeVisible();

    const tplAfter = await listTemplateByCode(accessToken, "ARTISAN_DEVIS");
    expect(tplAfter).not.toBeNull();
    await gotoAuthed(page, `/forms/${tplAfter!.currentVersionId}`);
    await fillDevis(page, "DEV-E2E-AFTER");
    const downloadPromise = page.waitForEvent("download", { timeout: 120_000 });
    await page.getByRole("button", { name: "Generer le document" }).click();
    await expect(page.getByText(/Document .+ genere/i)).toBeVisible({ timeout: 120_000 });
    const download = await downloadPromise;
    expect(download.suggestedFilename().toLowerCase()).toMatch(/\.(pdf|docx)$/);

    await gotoAuthed(page, `/documents/${oldDocId}`);
    await expect(page).toHaveURL(new RegExp(`/documents/${oldDocId}`));
    await expect(page.getByText("COMPLETED")).toBeVisible();
  });
});
