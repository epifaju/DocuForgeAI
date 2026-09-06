import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { expect, type Page } from "@playwright/test";
import { CREDENTIALS, loginApi, seedActiveTemplate, type SeededTemplate } from "./api";

const __dirname = dirname(fileURLToPath(import.meta.url));
export const DOCX_PATH = join(__dirname, "../fixtures/e2e-template.docx");
export const CSV_PATH = join(__dirname, "../fixtures/batch.csv");

export function readFixtureDocx(): Buffer {
  return readFileSync(DOCX_PATH);
}

export async function uiLogin(
  page: Page,
  creds = CREDENTIALS,
): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("Societe").fill(creds.companyIdentifier);
  await page.getByLabel("Email").fill(creds.email);
  await page.getByLabel("Mot de passe").fill(creds.password);
  await page.getByRole("button", { name: "Se connecter" }).click();
  await page.waitForURL("**/dashboard");
}

/**
 * Full reload drops the in-memory access token; re-login if bootstrap/refresh fails.
 */
export async function gotoAuthed(page: Page, path: string): Promise<void> {
  await page.goto(path);
  if (await page.getByRole("heading", { name: "Connexion" }).isVisible().catch(() => false)) {
    await uiLogin(page);
    await page.goto(path);
  }
  await expect(page).not.toHaveURL(/\/login/);
}

export async function seedTemplateForRun(): Promise<{
  token: string;
  template: SeededTemplate;
}> {
  const { accessToken } = await loginApi();
  const template = await seedActiveTemplate(accessToken, readFixtureDocx());
  return { token: accessToken, template };
}

/** Labels inferred from keys: client.firstName → "Client First Name" */
export const FIELD = {
  firstName: "Client First Name",
  email: "Client Email",
  total: "Invoice Total",
} as const;
