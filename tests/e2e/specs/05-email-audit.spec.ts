import { expect, test } from "@playwright/test";
import { clearMailpit, findMailpitMessage, waitForDocumentCompleted } from "../helpers/api";
import { FIELD, seedTemplateForRun, uiLogin } from "../helpers/auth";

test.describe("Email + Audit (§101.20–21)", () => {
  test("send document to Mailpit and see audit trail", async ({ page }) => {
    await clearMailpit();
    const { token, template } = await seedTemplateForRun();
    await uiLogin(page);

    await page.goto(`/forms/${template.currentVersionId}`);
    await page.getByLabel(FIELD.firstName, { exact: false }).fill("Carol");
    await page.getByLabel(FIELD.email, { exact: false }).fill("carol.e2e@example.com");
    await page.getByLabel(FIELD.total, { exact: false }).fill("7");

    const genDownload = page.waitForEvent("download", { timeout: 90_000 });
    await page.getByRole("button", { name: "Generer le document" }).click();
    await expect(page.getByText(/Document .+ genere/i)).toBeVisible({ timeout: 90_000 });
    await genDownload;

    const href = await page.getByRole("link", { name: "Voir dans le repository" }).getAttribute("href");
    const docId = href!.split("/").pop()!;
    await waitForDocumentCompleted(token, docId);

    await page.goto(`/documents/${docId}`);
    const recipient = `e2e-${Date.now()}@mailpit.local`;
    await page.getByLabel("Destinataire").fill(recipient);
    await page.getByRole("button", { name: "Envoyer…" }).click();
    await page.getByRole("button", { name: "Confirmer l'envoi" }).click();
    await expect(page.getByText(/Email envoye/i)).toBeVisible({ timeout: 30_000 });

    const msg = await findMailpitMessage(recipient);
    expect(msg.Subject).toBeTruthy();

    await page.getByRole("link", { name: "Audit" }).click();
    await expect(page.getByText("Journal d'audit", { exact: false })).toBeVisible();
    await page.getByLabel("Action").selectOption("LOGIN");
    await page.getByRole("button", { name: "Filtrer" }).click();
    await expect(page.locator("table, tbody").getByText("LOGIN").first()).toBeVisible();

    await page.getByLabel("Action").selectOption("DOCUMENT_EMAILED");
    await page.getByRole("button", { name: "Filtrer" }).click();
    await expect(page.locator("table").getByText("DOCUMENT_EMAILED").first()).toBeVisible();
  });
});
