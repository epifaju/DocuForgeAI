import { apiFetch, apiJson } from "./client";

export interface AdminSettings {
  company: {
    id: string;
    name: string;
    identifier: string;
  };
  ai: {
    platformEnabled: boolean;
    companyEnabled: boolean;
    effectivelyEnabled: boolean;
    provider: string;
    model: string;
  };
  email: {
    platformEnabled: boolean;
    platformFrom: string;
    fromAddress: string;
    maxAttachmentBytes: number;
    requireConfirmation: boolean;
  };
  privacy: {
    retentionDays: number;
  };
}

export interface AdminSettingsUpdate {
  company: { name: string };
  ai: { companyEnabled: boolean };
  email: { fromAddress: string };
  privacy: { retentionDays: number };
}

export function getSettings(token: string) {
  return apiJson<AdminSettings>("/api/v1/admin/settings", { token });
}

export function updateSettings(token: string, body: AdminSettingsUpdate) {
  return apiJson<AdminSettings>("/api/v1/admin/settings", {
    method: "PUT",
    token,
    body: JSON.stringify(body),
  });
}

export async function exportMyData(token: string): Promise<Blob> {
  const res = await apiFetch("/api/v1/privacy/export", { token, json: false });
  if (!res.ok) {
    throw new Error("Export impossible");
  }
  return res.blob();
}

export function deleteMyAccount(token: string) {
  return apiJson<null>("/api/v1/privacy/me", { method: "DELETE", token });
}

export function purgeRetention(token: string) {
  return apiJson<{ deleted: number }>("/api/v1/privacy/purge", {
    method: "POST",
    token,
  });
}
