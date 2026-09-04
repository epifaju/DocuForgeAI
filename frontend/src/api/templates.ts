import { apiJson, bearerHeaders } from "./client";
import type { PageResponse, TemplateSummary } from "./types";

export type { TemplateSummary };

export interface TemplateDetail extends TemplateSummary {
  description?: string | null;
  category?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export function createTemplate(
  token: string,
  body: { code: string; name: string; description?: string; category?: string },
) {
  return apiJson<TemplateDetail>("/api/v1/templates", {
    method: "POST",
    token,
    body: JSON.stringify(body),
  });
}

export async function uploadTemplateVersion(
  token: string,
  templateId: string,
  file: File,
  setAsCurrent = true,
) {
  const form = new FormData();
  form.append("file", file);
  const response = await fetch(
    `/api/v1/templates/${templateId}/versions?setAsCurrent=${setAsCurrent}`,
    {
      method: "POST",
      headers: bearerHeaders(token),
      body: form,
    },
  );
  const json = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(json?.message ?? "Upload DOCX impossible");
  }
  return json.data as { id: string; versionNumber: number; originalFilename: string };
}

export function activateTemplate(token: string, templateId: string) {
  return apiJson<TemplateDetail>(`/api/v1/templates/${templateId}/activate`, {
    method: "POST",
    token,
  });
}

export function archiveTemplate(token: string, templateId: string) {
  return apiJson<TemplateDetail>(`/api/v1/templates/${templateId}/archive`, {
    method: "POST",
    token,
  });
}

export function listTemplatesPage(
  token: string,
  opts: { page?: number; size?: number } = {},
) {
  const page = opts.page ?? 0;
  const size = opts.size ?? 50;
  return apiJson<PageResponse<TemplateSummary>>(
    `/api/v1/templates?page=${page}&size=${size}`,
    { token },
  );
}
