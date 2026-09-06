import { createHash } from "node:crypto";
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import JSZip from "jszip";
import { E2E_API } from "./api";

const __dirname = dirname(fileURLToPath(import.meta.url));

/** Official demo pack ZIP from Phase 21 (repo docs, not a secret). */
export const ARTISAN_PACK_KEY = "com.docuforge.pack.artisan-demo";
export const ARTISAN_ZIP_SOURCE = join(
  __dirname,
  "../../../docs/business-packs/packs/artisan-demo/dist/docuforge-pack-artisan-demo-1.0.0.zip",
);
export const ARTISAN_ZIP_FIXTURE = join(__dirname, "../fixtures/docuforge-pack-artisan-demo-1.0.0.zip");
export const ARTISAN_ZIP_V110 = join(__dirname, "../fixtures/docuforge-pack-artisan-demo-1.1.0.zip");

export function ensureArtisanZipFixtures(): void {
  if (!existsSync(ARTISAN_ZIP_SOURCE)) {
    throw new Error(`Missing artisan demo ZIP at ${ARTISAN_ZIP_SOURCE}`);
  }
  mkdirSync(dirname(ARTISAN_ZIP_FIXTURE), { recursive: true });
  copyFileSync(ARTISAN_ZIP_SOURCE, ARTISAN_ZIP_FIXTURE);
}

type PackListItem = {
  id: string;
  packKey: string;
  name: string;
  status: string;
  currentVersion?: string | null;
};

type PagePacks = { items: PackListItem[]; totalElements: number };

async function apiJson<T>(
  path: string,
  options: RequestInit & { token?: string } = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.token) headers.set("Authorization", `Bearer ${options.token}`);
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const res = await fetch(`${E2E_API}${path}`, { ...options, headers });
  const body = (await res.json().catch(() => null)) as {
    data?: T;
    message?: string;
    code?: string;
  } | null;
  if (!res.ok) {
    throw new Error(
      `${options.method ?? "GET"} ${path} → ${res.status} ${body?.code ?? ""} ${body?.message ?? ""}`,
    );
  }
  return body?.data as T;
}

export async function findPackByKey(
  token: string,
  packKey: string,
): Promise<PackListItem | null> {
  const page = await apiJson<PagePacks>(
    `/api/v1/admin/business-packs?search=${encodeURIComponent(packKey)}&size=50`,
    { token },
  );
  return page.items.find((p) => p.packKey === packKey) ?? null;
}

/** Soft-uninstall so the same 1.0.0 ZIP can be reinstalled in a fresh E2E run. */
export async function ensureArtisanPackUninstalled(token: string): Promise<void> {
  const pack = await findPackByKey(token, ARTISAN_PACK_KEY);
  if (!pack) return;
  if (pack.status === "UNINSTALLED") return;
  await apiJson(`/api/v1/admin/business-packs/${pack.id}`, {
    method: "DELETE",
    token,
  });
}

export async function listTemplateByCode(
  token: string,
  code: string,
): Promise<{ id: string; code: string; name: string; currentVersionId: string } | null> {
  const page = await apiJson<{
    items: Array<{
      id: string;
      code: string;
      name: string;
      currentVersionId?: string | null;
    }>;
  }>(`/api/v1/templates?q=${encodeURIComponent(code)}&size=50`, { token });
  const hit = page.items.find((t) => t.code === code);
  if (!hit?.currentVersionId) return null;
  return {
    id: hit.id,
    code: hit.code,
    name: hit.name,
    currentVersionId: hit.currentVersionId,
  };
}

/**
 * Next unused `1.N.0` for artisan-demo on the shared E2E backend
 * (avoids `error.pack.version_already_installed` and SemVer STRICT overflow).
 */
export async function nextFreeArtisanMinorVersion(token: string): Promise<string> {
  const pack = await findPackByKey(token, ARTISAN_PACK_KEY);
  if (!pack) return "1.1.0";
  const detail = await apiJson<{ versions: Array<{ version: string }> }>(
    `/api/v1/admin/business-packs/${pack.id}`,
    { token },
  );
  const taken = new Set((detail.versions ?? []).map((v) => v.version));
  for (let minor = 1; minor <= 10_000; minor++) {
    const candidate = `1.${minor}.0`;
    if (!taken.has(candidate)) return candidate;
  }
  throw new Error("No free artisan-demo minor version below 1.10000.0");
}

/**
 * Build a MINOR update pack from 1.0.0 by bumping SemVer + pack description
 * and recalculating sha256 checksums (PRD §189).
 */
export async function materializeArtisanPackV110(
  targetVersion = "1.1.0",
): Promise<{ path: string; version: string }> {
  ensureArtisanZipFixtures();
  const zip = await JSZip.loadAsync(readFileSync(ARTISAN_ZIP_FIXTURE));
  const manifestFile = zip.file("manifest.json");
  if (!manifestFile) throw new Error("manifest.json missing in artisan ZIP");
  const manifest = JSON.parse(await manifestFile.async("string")) as {
    version: string;
    description?: string;
    checksums?: Record<string, string>;
    templates?: Array<{ version?: string }>;
  };
  manifest.version = targetVersion;
  manifest.description =
    (manifest.description ?? "Artisan demo") + ` (E2E minor update ${targetVersion})`;
  if (Array.isArray(manifest.templates)) {
    for (const tpl of manifest.templates) {
      tpl.version = targetVersion;
    }
  }

  const checksums: Record<string, string> = {};
  for (const logical of Object.keys(manifest.checksums ?? {})) {
    const entry = zip.file(logical);
    if (!entry) throw new Error(`Checksum path missing in ZIP: ${logical}`);
    const bytes = await entry.async("nodebuffer");
    checksums[logical] = `sha256:${createHash("sha256").update(bytes).digest("hex")}`;
  }
  manifest.checksums = checksums;
  zip.file("manifest.json", JSON.stringify(manifest, null, 2));

  const out = await zip.generateAsync({ type: "nodebuffer", compression: "DEFLATE" });
  writeFileSync(ARTISAN_ZIP_V110, out);
  return { path: ARTISAN_ZIP_V110, version: targetVersion };
}
