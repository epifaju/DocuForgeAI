import { apiJson } from "./client";

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
}

export interface AdminSettingsUpdate {
  company: { name: string };
  ai: { companyEnabled: boolean };
  email: { fromAddress: string };
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
