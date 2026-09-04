import { apiJson } from "./client";
import type { GeneratedDocument } from "./documents";
import type { FormSchema, PageResponse, TemplateSummary } from "./types";

export type { GeneratedDocument } from "./documents";

export function listTemplates(
  token: string,
  opts: { page?: number; size?: number; status?: string; q?: string } = {},
) {
  const params = new URLSearchParams();
  params.set("page", String(opts.page ?? 0));
  params.set("size", String(opts.size ?? 50));
  if (opts.status) params.set("status", opts.status);
  if (opts.q?.trim()) params.set("q", opts.q.trim());
  return apiJson<PageResponse<TemplateSummary>>(`/api/v1/templates?${params}`, { token });
}

export function getFormSchema(token: string, versionId: string) {
  return apiJson<FormSchema>(`/api/v1/template-versions/${versionId}/form-schema`, { token });
}

export function validateFormData(
  token: string,
  versionId: string,
  data: Record<string, unknown>,
) {
  return apiJson<{ valid: boolean }>(`/api/v1/template-versions/${versionId}/validate`, {
    method: "POST",
    token,
    body: JSON.stringify({ data }),
  });
}

export function generateDocument(
  token: string,
  templateId: string,
  templateVersionId: string,
  data: Record<string, unknown>,
  title?: string,
) {
  return apiJson<GeneratedDocument>("/api/v1/documents/generate", {
    method: "POST",
    token,
    body: JSON.stringify({ templateId, templateVersionId, title, data }),
  });
}
