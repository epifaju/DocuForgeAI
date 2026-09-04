import { apiJson, bearerHeaders } from "./client";
import type { PageResponse } from "./types";

export interface BatchJob {
  id: string;
  status: string;
  templateId: string;
  templateVersionId: string;
  totalItems: number;
  processedItems: number;
  successfulItems: number;
  failedItems: number;
  zipReady: boolean;
  errorsReady: boolean;
  createdBy?: string | null;
  createdAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
}

export interface BatchErrorItem {
  rowNumber: number;
  message: string;
}

export function listBatches(token: string) {
  return apiJson<PageResponse<BatchJob>>("/api/v1/batches?size=50", { token });
}

export function getBatch(token: string, id: string) {
  return apiJson<BatchJob>(`/api/v1/batches/${id}`, { token });
}

export function getBatchErrors(token: string, id: string) {
  return apiJson<BatchErrorItem[]>(`/api/v1/batches/${id}/errors`, { token });
}

export async function createBatch(
  token: string,
  file: File,
  templateId: string,
  mapping?: Record<string, string>,
) {
  const form = new FormData();
  form.append("file", file);
  form.append("templateId", templateId);
  if (mapping) {
    form.append("mapping", JSON.stringify(mapping));
  }
  const response = await fetch("/api/v1/batches", {
    method: "POST",
    headers: bearerHeaders(token),
    body: form,
  });
  const json = await response.json();
  if (!response.ok) {
    throw new Error(json.message ?? "Batch impossible");
  }
  return json.data as BatchJob;
}

export async function downloadBatchZip(token: string, id: string) {
  const response = await fetch(`/api/v1/batches/${id}/download`, {
    headers: bearerHeaders(token),
  });
  if (!response.ok) throw new Error("ZIP indisponible");
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `batch-${id}.zip`;
  a.click();
  URL.revokeObjectURL(url);
}
