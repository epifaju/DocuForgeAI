import { apiFetch, apiJson, ApiError } from "./client";
import type { ErrorResponse, PageResponse } from "./types";

export type BusinessPackStatus =
  | "INSTALLED"
  | "DISABLED"
  | "UPDATE_AVAILABLE"
  | "BROKEN"
  | "UNINSTALLED";

export type BusinessPackType = "OFFICIAL" | "CUSTOM" | "THIRD_PARTY";

export interface PackSummary {
  id: string;
  packKey: string;
  slug: string;
  name: string;
  description?: string | null;
  packType: BusinessPackType;
  publisherId?: string | null;
  publisherName?: string | null;
  status: BusinessPackStatus;
  currentVersion?: string | null;
  currentVersionId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PackVersion {
  id: string;
  version: string;
  schemaVersion: string;
  minimumDocuForgeVersion?: string | null;
  maximumDocuForgeVersion?: string | null;
  archiveChecksum?: string | null;
  status: string;
  installedAt?: string | null;
  createdAt: string;
  current: boolean;
}

export interface PackTemplate {
  id: string;
  templateCode: string;
  name: string;
  templateStatus?: string | null;
  templateId?: string | null;
  templateVersionId?: string | null;
  templateVersionNumber?: number | null;
  enabledByDefault: boolean;
  enabled: boolean;
}

export interface PackPrompt {
  id: string;
  promptCode: string;
  promptVersion: string;
  content: string;
  checksum: string;
}

export interface PackInstallation {
  id: string;
  packVersionId?: string | null;
  packVersion?: string | null;
  installationType: string;
  status: string;
  installedBy?: string | null;
  installedAt: string;
  disabledAt?: string | null;
  uninstalledAt?: string | null;
}

export interface PackDetail {
  id: string;
  packKey: string;
  slug: string;
  name: string;
  description?: string | null;
  packType: BusinessPackType;
  publisherId?: string | null;
  publisherName?: string | null;
  status: BusinessPackStatus;
  currentVersion?: PackVersion | null;
  versions: PackVersion[];
  templates: PackTemplate[];
  prompts: PackPrompt[];
  installation?: PackInstallation | null;
  createdAt: string;
  updatedAt: string;
}

export type ListPacksParams = {
  status?: string;
  type?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
};

function packsQuery(params: ListPacksParams = {}): string {
  const q = new URLSearchParams();
  if (params.status) q.set("status", params.status);
  if (params.type) q.set("type", params.type);
  if (params.search) q.set("search", params.search);
  if (params.page != null) q.set("page", String(params.page));
  if (params.size != null) q.set("size", String(params.size));
  if (params.sort) q.set("sort", params.sort);
  const qs = q.toString();
  return qs ? `?${qs}` : "";
}

export function listBusinessPacks(token: string, params: ListPacksParams = {}) {
  return apiJson<PageResponse<PackSummary>>(
    `/api/v1/admin/business-packs${packsQuery(params)}`,
    { token },
  );
}

export function getBusinessPack(token: string, packId: string) {
  return apiJson<PackDetail>(`/api/v1/admin/business-packs/${packId}`, { token });
}

export function listBusinessPackVersions(token: string, packId: string) {
  return apiJson<PackVersion[]>(`/api/v1/admin/business-packs/${packId}/versions`, {
    token,
  });
}

export function listBusinessPackTemplates(token: string, packId: string) {
  return apiJson<PackTemplate[]>(`/api/v1/admin/business-packs/${packId}/templates`, {
    token,
  });
}

export function enableBusinessPack(token: string, packId: string) {
  return apiJson<PackSummary>(`/api/v1/admin/business-packs/${packId}/enable`, {
    method: "POST",
    token,
  });
}

export function disableBusinessPack(token: string, packId: string) {
  return apiJson<PackSummary>(`/api/v1/admin/business-packs/${packId}/disable`, {
    method: "POST",
    token,
  });
}

export interface PackUninstallResult {
  packId: string;
  packKey: string;
  status: BusinessPackStatus;
  templatesArchived: number;
  historicalDocumentCount: number;
  metadataPreserved: boolean;
}

export function uninstallBusinessPack(token: string, packId: string) {
  return apiJson<PackUninstallResult>(`/api/v1/admin/business-packs/${packId}`, {
    method: "DELETE",
    token,
  });
}

export async function exportBusinessPack(token: string, packId: string, fallbackName = "pack.zip") {
  const response = await apiFetch(`/api/v1/admin/business-packs/${packId}/export`, {
    token,
    json: false,
  });
  if (!response.ok) {
    throw new Error("Export pack impossible");
  }
  const blob = await response.blob();
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  const filename = match?.[1] ?? fallbackName;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename.endsWith(".zip") ? filename : `${filename}.zip`;
  a.click();
  URL.revokeObjectURL(url);
}

export function enablePackTemplate(token: string, packId: string, templateCode: string) {
  return apiJson<PackTemplate>(
    `/api/v1/admin/business-packs/${packId}/templates/${encodeURIComponent(templateCode)}/enable`,
    { method: "POST", token },
  );
}

export function disablePackTemplate(token: string, packId: string, templateCode: string) {
  return apiJson<PackTemplate>(
    `/api/v1/admin/business-packs/${packId}/templates/${encodeURIComponent(templateCode)}/disable`,
    { method: "POST", token },
  );
}

export type PackImportJobStatus =
  | "UPLOADED"
  | "VALIDATING"
  | "VALID"
  | "INVALID"
  | "INSTALLING"
  | "INSTALLED"
  | "FAILED"
  | "EXPIRED";

export interface PackValidationIssue {
  severity: "ERROR" | "WARNING" | string;
  code: string;
  message: string;
  file?: string | null;
  templateCode?: string | null;
  variable?: string | null;
}

export interface PackValidationReport {
  valid: boolean;
  pack?: { id?: string; name?: string; version?: string } | null;
  summary?: {
    errors?: number;
    warnings?: number;
    templates?: number;
    prompts?: number;
  } | null;
  issues?: PackValidationIssue[];
}

export interface PackImportJob {
  jobId: string;
  status: PackImportJobStatus;
  originalFilename?: string | null;
  detectedPackKey?: string | null;
  detectedVersion?: string | null;
  validationReport?: PackValidationReport | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  completedAt?: string | null;
  expiresAt?: string | null;
}

export interface PackInstallResult {
  jobId: string;
  packId: string;
  packVersionId: string;
  installationId: string;
  packKey: string;
  version: string;
  installationType: string;
  installationStatus: string;
  templatesInstalled: number;
  promptsInstalled: number;
}

export async function uploadPackImport(token: string, file: File) {
  const form = new FormData();
  form.append("file", file);
  const response = await apiFetch("/api/v1/admin/business-packs/import", {
    method: "POST",
    token,
    json: false,
    body: form,
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(
      (body as ErrorResponse) ?? {
        timestamp: new Date().toISOString(),
        status: response.status,
        code: "REQUEST_ERROR",
        message: "Import pack impossible.",
        details: [],
      },
    );
  }
  return (body as { data: PackImportJob }).data;
}

export function getPackImport(token: string, jobId: string) {
  return apiJson<PackImportJob>(`/api/v1/admin/business-packs/imports/${jobId}`, { token });
}

export function validatePackImport(token: string, jobId: string) {
  return apiJson<PackImportJob>(`/api/v1/admin/business-packs/imports/${jobId}/validate`, {
    method: "POST",
    token,
  });
}

export function installPackImport(
  token: string,
  jobId: string,
  body: { enablePack?: boolean; enableTemplates?: boolean } = {},
) {
  return apiJson<PackInstallResult>(`/api/v1/admin/business-packs/imports/${jobId}/install`, {
    method: "POST",
    token,
    body: JSON.stringify({
      enablePack: body.enablePack ?? true,
      enableTemplates: body.enableTemplates ?? true,
    }),
  });
}

export interface PackChangeItem {
  kind: string;
  templateOrPromptCode?: string | null;
  variableKey?: string | null;
  before?: string | null;
  after?: string | null;
}

export interface PackUpdatePreview {
  updateCandidate: boolean;
  freshInstall: boolean;
  packId?: string | null;
  packKey?: string | null;
  installedVersion?: string | null;
  candidateVersion?: string | null;
  updateKind?: string | null;
  semverBreakingMismatch: boolean;
  summary: {
    templatesAdded: number;
    templatesUpdated: number;
    templatesRemoved: number;
    variablesAdded: number;
    variablesRemoved: number;
    requiredVariablesAdded: number;
    promptsChanged: number;
  };
  changes: PackChangeItem[];
  breakingChanges: PackValidationIssue[];
}

export function getPackUpdatePreview(token: string, jobId: string) {
  return apiJson<PackUpdatePreview>(
    `/api/v1/admin/business-packs/imports/${jobId}/update-preview`,
    { token },
  );
}
