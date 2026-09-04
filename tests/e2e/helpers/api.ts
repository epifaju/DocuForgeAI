export const E2E_API = process.env.E2E_API_URL ?? "http://localhost:18081";
export const E2E_MAILPIT = process.env.E2E_MAILPIT_URL ?? "http://localhost:8028";

export const CREDENTIALS = {
  companyIdentifier: process.env.E2E_COMPANY ?? "demo",
  email: process.env.E2E_EMAIL ?? "admin@demo.local",
  password: process.env.E2E_PASSWORD ?? "changeme_admin_dev_only",
};

const DOCX_MIME =
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

type Json = Record<string, unknown>;

async function api<T>(
  path: string,
  options: RequestInit & { token?: string } = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.token) headers.set("Authorization", `Bearer ${options.token}`);
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const res = await fetch(`${E2E_API}${path}`, { ...options, headers });
  const body = (await res.json().catch(() => null)) as { data?: T; message?: string; code?: string } | null;
  if (!res.ok) {
    throw new Error(`${options.method ?? "GET"} ${path} → ${res.status} ${body?.code ?? ""} ${body?.message ?? ""}`);
  }
  return body?.data as T;
}

export async function loginApi(
  creds = CREDENTIALS,
): Promise<{ accessToken: string }> {
  return api("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify(creds),
  });
}

export interface SeededTemplate {
  id: string;
  code: string;
  name: string;
  currentVersionId: string;
  currentVersionNumber: number;
}

export async function seedActiveTemplate(
  token: string,
  docxBytes: Buffer,
  suffix = Date.now().toString(36),
): Promise<SeededTemplate> {
  const code = `e2e_${suffix}`.slice(0, 40);
  const name = `E2E Template ${suffix}`;

  const created = await api<{ id: string; code: string; name: string }>("/api/v1/templates", {
    method: "POST",
    token,
    body: JSON.stringify({ code, name, description: "Playwright Phase 20", category: "e2e" }),
  });

  const form = new FormData();
  form.append(
    "file",
    new Blob([new Uint8Array(docxBytes)], { type: DOCX_MIME }),
    "e2e-template.docx",
  );

  await api(`/api/v1/templates/${created.id}/versions?setAsCurrent=true`, {
    method: "POST",
    token,
    body: form,
  });

  await api(`/api/v1/templates/${created.id}/activate`, {
    method: "POST",
    token,
  });

  const tpl = await api<{
    id: string;
    code: string;
    name: string;
    currentVersionId: string;
    currentVersionNumber: number;
  }>(`/api/v1/templates/${created.id}`, { token });

  if (!tpl.currentVersionId) {
    throw new Error("Template has no currentVersionId after activate");
  }

  return {
    id: tpl.id,
    code: tpl.code,
    name: tpl.name,
    currentVersionId: tpl.currentVersionId,
    currentVersionNumber: tpl.currentVersionNumber,
  };
}

export async function getDocumentApi(token: string, id: string): Promise<Json> {
  return api(`/api/v1/documents/${id}`, { token });
}

export async function waitForDocumentCompleted(
  token: string,
  id: string,
  timeoutMs = 90_000,
): Promise<Json> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const doc = await getDocumentApi(token, id);
    if (doc.status === "COMPLETED" && doc.pdfStorageKey) return doc;
    if (doc.status === "FAILED") {
      throw new Error(`Document ${id} FAILED: ${JSON.stringify(doc)}`);
    }
    await new Promise((r) => setTimeout(r, 1500));
  }
  throw new Error(`Timeout waiting for document ${id} COMPLETED`);
}

export async function clearMailpit(): Promise<void> {
  await fetch(`${E2E_MAILPIT}/api/v1/messages`, { method: "DELETE" });
}

export async function findMailpitMessage(toContains: string, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const res = await fetch(`${E2E_MAILPIT}/api/v1/messages`);
    const body = (await res.json()) as {
      messages?: Array<{ ID: string; To: Array<{ Address: string }>; Subject: string }>;
    };
    const hit = (body.messages ?? []).find((m) =>
      (m.To ?? []).some((t) => t.Address?.toLowerCase().includes(toContains.toLowerCase())),
    );
    if (hit) return hit;
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`No Mailpit message to *${toContains}* within ${timeoutMs}ms`);
}
