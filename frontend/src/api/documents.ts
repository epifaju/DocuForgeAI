import { apiJson } from "./client";
import type { PageResponse } from "./types";

export interface GeneratedDocument {
  id: string;
  reference: string;
  title: string;
  status: string;
  templateId: string;
  templateCode?: string | null;
  templateName?: string | null;
  templateVersionId: string;
  templateVersionNumber?: number | null;
  documentVersionNumber?: number | null;
  rootDocumentId?: string | null;
  parentDocumentId?: string | null;
  docxStorageKey?: string | null;
  pdfStorageKey?: string | null;
  checksum?: string | null;
  createdBy?: string | null;
  createdByName?: string | null;
  createdAt: string;
  data?: Record<string, unknown> | null;
}

export interface DocumentListParams {
  q?: string;
  status?: string;
  templateId?: string;
  createdBy?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
}

export function listDocuments(token: string, params: DocumentListParams = {}) {
  const query = new URLSearchParams();
  if (params.q) query.set("q", params.q);
  if (params.status) query.set("status", params.status);
  if (params.templateId) query.set("templateId", params.templateId);
  if (params.createdBy) query.set("createdBy", params.createdBy);
  if (params.createdFrom) query.set("createdFrom", params.createdFrom);
  if (params.createdTo) query.set("createdTo", params.createdTo);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
  return apiJson<PageResponse<GeneratedDocument>>(`/api/v1/documents?${query}`, { token });
}

export function getDocument(token: string, id: string) {
  return apiJson<GeneratedDocument>(`/api/v1/documents/${id}`, { token });
}

export function listDocumentVersions(token: string, id: string) {
  return apiJson<GeneratedDocument[]>(`/api/v1/documents/${id}/versions`, { token });
}

export function createDocumentVersion(
  token: string,
  sourceId: string,
  data: Record<string, unknown>,
  title?: string,
) {
  return apiJson<GeneratedDocument>(`/api/v1/documents/${sourceId}/new-version`, {
    method: "POST",
    token,
    body: JSON.stringify({ title, data }),
  });
}

export async function downloadGeneratedDocx(token: string, documentId: string, filename: string) {
  await downloadBinary(token, `/api/v1/documents/${documentId}/download/docx`, filename, ".docx");
}

export async function downloadGeneratedPdf(token: string, documentId: string, filename: string) {
  await downloadBinary(token, `/api/v1/documents/${documentId}/download/pdf`, filename, ".pdf");
}

export async function fetchPdfBlob(token: string, documentId: string): Promise<Blob> {
  const response = await fetch(`/api/v1/documents/${documentId}/download/pdf?preview=true`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error("Apercu PDF impossible");
  }
  return response.blob();
}

export type AttachmentFormat = "PDF" | "DOCX" | "BOTH";

export interface DocumentEmailPayload {
  recipient: string;
  subject: string;
  message?: string;
  attachmentFormat: AttachmentFormat;
  confirmed: boolean;
}

export interface DocumentEmailResult {
  id: string;
  documentId: string;
  recipient: string;
  subject: string;
  attachmentFormat: AttachmentFormat;
  status: string;
  createdAt: string;
  sentAt?: string | null;
}

export function emailDocument(token: string, documentId: string, payload: DocumentEmailPayload) {
  return apiJson<DocumentEmailResult>(`/api/v1/documents/${documentId}/email`, {
    method: "POST",
    token,
    body: JSON.stringify(payload),
  });
}

async function downloadBinary(token: string, path: string, filename: string, ext: string) {
  const response = await fetch(path, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Telechargement ${ext} impossible`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename.endsWith(ext) ? filename : `${filename}${ext}`;
  a.click();
  URL.revokeObjectURL(url);
}
