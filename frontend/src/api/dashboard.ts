import { apiJson } from "./client";

export interface DashboardKpis {
  documentsGeneratedToday: number;
  documentsGeneratedThisMonth: number;
  activeTemplates: number;
  failedGenerations: number;
  batchJobsTotal: number;
  batchJobsActive: number;
  aiRequestsToday: number;
  aiRequestsThisMonth: number;
}

export interface DashboardRecentDocument {
  id: string;
  reference: string;
  title: string;
  status: string;
  templateName?: string | null;
  createdAt: string;
}

export interface DashboardActivity {
  id: string;
  action: string;
  entityType?: string | null;
  entityId?: string | null;
  status: string;
  userEmail?: string | null;
  createdAt: string;
}

export interface DashboardData {
  kpis: DashboardKpis;
  recentDocuments: DashboardRecentDocument[];
  recentActivity: DashboardActivity[];
  timezone: string;
}

export function fetchDashboard(token: string) {
  return apiJson<DashboardData>("/api/v1/dashboard", { token });
}
