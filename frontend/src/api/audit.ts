import { apiJson } from "./client";
import type { PageResponse } from "./types";

export interface AuditLogItem {
  id: string;
  action: string;
  entityType?: string | null;
  entityId?: string | null;
  status: string;
  ipAddress?: string | null;
  metadata?: string | null;
  userId?: string | null;
  userEmail?: string | null;
  createdAt: string;
}

export interface AuditListParams {
  action?: string;
  entityType?: string;
  status?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
}

export function listAudit(token: string, params: AuditListParams = {}) {
  const query = new URLSearchParams();
  if (params.action) query.set("action", params.action);
  if (params.entityType) query.set("entityType", params.entityType);
  if (params.status) query.set("status", params.status);
  if (params.createdFrom) query.set("createdFrom", params.createdFrom);
  if (params.createdTo) query.set("createdTo", params.createdTo);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
  return apiJson<PageResponse<AuditLogItem>>(`/api/v1/audit?${query}`, { token });
}
