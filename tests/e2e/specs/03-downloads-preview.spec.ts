import { expect, test } from "@playwright/test";
import { E2E_API, waitForDocumentCompleted } from "../helpers/api";
import { seedTemplateForRun, uiLogin } from "../helpers/auth";

test.describe("Downloads & PDF preview (§101.11–12)", () => {
  test("DOCX/PDF download and PDF preview iframe", async ({ page, request }) => {
    const { token, template } = await seedTemplateForRun();

    const gen = await request.post(`${E2E_API}/api/v1/documents/generate`, {
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      data: {
        templateId: template.id,
        templateVersionId: template.currentVersionId,
        title: "E2E download check",
        data: {
          "client.firstName": "Bob",
          "client.email": "bob.e2e@example.com",
          "invoice.total": 99.5,
        },
      },
    });
    expect(gen.ok()).toBeTruthy();
    const body = await gen.json();
    const docId = body.data.id as string;
    await waitForDocumentCompleted(token, docId);

    // API-level binary integrity (Playwright can inspect downloaded bytes)
    const docxApi = await request.get(`${E2E_API}/api/v1/documents/${docId}/download/docx`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(docxApi.ok()).toBeTruthy();
    expect(Buffer.from(await docxApi.body()).subarray(0, 2).toString("ascii")).toBe("PK");

    const pdfApi = await request.get(`${E2E_API}/api/v1/documents/${docId}/download/pdf`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(pdfApi.ok()).toBeTruthy();
    expect(Buffer.from(await pdfApi.body()).subarray(0, 4).toString("ascii")).toBe("%PDF");

    await uiLogin(page);
    await page.goto(`/documents/${docId}`);
    await expect(page.getByRole("button", { name: "Telecharger DOCX" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Telecharger PDF" })).toBeVisible();
    await expect(page.getByText("Apercu PDF")).toBeVisible();
    await expect(page.locator('iframe[title^="Apercu "]')).toBeVisible({ timeout: 60_000 });

    // UI: confirm click hits the right endpoints (blob a[download] filename is opaque in Chromium)
    const docxReq = page.waitForRequest((r) => r.url().includes(`/documents/${docId}/download/docx`));
    await page.getByRole("button", { name: "Telecharger DOCX" }).click();
    await docxReq;

    const pdfReq = page.waitForRequest((r) => r.url().includes(`/documents/${docId}/download/pdf`) && !r.url().includes("preview=true"));
    await page.getByRole("button", { name: "Telecharger PDF" }).click();
    await pdfReq;
  });
});
