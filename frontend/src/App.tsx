import { Navigate, Route, Routes } from "react-router-dom";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/auth/AuthContext";
import { AuditPage } from "@/pages/AuditPage";
import { BatchDetailPage } from "@/pages/BatchDetailPage";
import { BatchesPage } from "@/pages/BatchesPage";
import { BusinessPackDetailPage } from "@/pages/BusinessPackDetailPage";
import { BusinessPackImportPage } from "@/pages/BusinessPackImportPage";
import { BusinessPacksPage } from "@/pages/BusinessPacksPage";
import { DashboardPage } from "@/pages/DashboardPage";
import { DocumentDetailPage } from "@/pages/DocumentDetailPage";
import { DocumentNewVersionPage } from "@/pages/DocumentNewVersionPage";
import { DocumentsPage } from "@/pages/DocumentsPage";
import { FormPage } from "@/pages/FormPage";
import { LoginPage } from "@/pages/LoginPage";
import { ForgotPasswordPage } from "@/pages/ForgotPasswordPage";
import { ResetPasswordPage } from "@/pages/ResetPasswordPage";
import { PrivacyPage } from "@/pages/PrivacyPage";
import { SettingsPage } from "@/pages/SettingsPage";
import { TemplatesPage } from "@/pages/TemplatesPage";
import { UsersPage } from "@/pages/UsersPage";
import { UserDetailPage } from "@/pages/UserDetailPage";

function AuthSkeleton() {
  const { t } = useTranslation();
  return (
    <div
      className="flex min-h-screen flex-col"
      aria-busy="true"
      aria-live="polite"
      role="status"
    >
      <span className="sr-only">{t("common.authLoading")}</span>
      <div className="border-b border-[var(--line)] bg-[var(--surface)]/90 px-6 py-4">
        <div className="mx-auto flex max-w-6xl items-center gap-4">
          <div className="h-7 w-36 animate-pulse rounded-md bg-[var(--brand-soft)]" />
          <div className="hidden h-4 w-24 animate-pulse rounded bg-[var(--neutral-soft)] sm:block" />
          <div className="hidden h-4 w-24 animate-pulse rounded bg-[var(--neutral-soft)] sm:block" />
        </div>
      </div>
      <div className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">
        <div className="mb-8 h-9 w-48 animate-pulse rounded-md bg-[var(--brand-soft)]" />
        <div className="h-40 animate-pulse rounded-[var(--radius-xl)] border border-[var(--line)] bg-[var(--surface)]" />
      </div>
    </div>
  );
}

function Protected({ children }: { children: ReactNode }) {
  const { token, ready } = useAuth();
  if (!ready) return <AuthSkeleton />;
  if (!token) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route
        path="/dashboard"
        element={
          <Protected>
            <DashboardPage />
          </Protected>
        }
      />
      <Route
        path="/templates"
        element={
          <Protected>
            <TemplatesPage />
          </Protected>
        }
      />
      <Route
        path="/documents"
        element={
          <Protected>
            <DocumentsPage />
          </Protected>
        }
      />
      <Route
        path="/documents/:id/new-version"
        element={
          <Protected>
            <DocumentNewVersionPage />
          </Protected>
        }
      />
      <Route
        path="/documents/:id"
        element={
          <Protected>
            <DocumentDetailPage />
          </Protected>
        }
      />
      <Route
        path="/batches"
        element={
          <Protected>
            <BatchesPage />
          </Protected>
        }
      />
      <Route
        path="/batches/:id"
        element={
          <Protected>
            <BatchDetailPage />
          </Protected>
        }
      />
      <Route
        path="/audit"
        element={
          <Protected>
            <AuditPage />
          </Protected>
        }
      />
      <Route
        path="/users"
        element={
          <Protected>
            <UsersPage />
          </Protected>
        }
      />
      <Route
        path="/users/:id"
        element={
          <Protected>
            <UserDetailPage />
          </Protected>
        }
      />
      <Route
        path="/business-packs"
        element={
          <Protected>
            <BusinessPacksPage />
          </Protected>
        }
      />
      <Route
        path="/business-packs/import"
        element={
          <Protected>
            <BusinessPackImportPage />
          </Protected>
        }
      />
      <Route
        path="/business-packs/import/:jobId"
        element={
          <Protected>
            <BusinessPackImportPage />
          </Protected>
        }
      />
      <Route
        path="/business-packs/:packId"
        element={
          <Protected>
            <BusinessPackDetailPage />
          </Protected>
        }
      />
      <Route
        path="/business-packs/:packId/templates"
        element={
          <Protected>
            <BusinessPackDetailPage />
          </Protected>
        }
      />
      <Route
        path="/business-packs/:packId/versions"
        element={
          <Protected>
            <BusinessPackDetailPage />
          </Protected>
        }
      />
      <Route
        path="/settings"
        element={
          <Protected>
            <Navigate to="/settings/company" replace />
          </Protected>
        }
      />
      <Route
        path="/settings/company"
        element={
          <Protected>
            <SettingsPage />
          </Protected>
        }
      />
      <Route
        path="/settings/ai"
        element={
          <Protected>
            <SettingsPage />
          </Protected>
        }
      />
      <Route
        path="/settings/email"
        element={
          <Protected>
            <SettingsPage />
          </Protected>
        }
      />
      <Route
        path="/settings/privacy"
        element={
          <Protected>
            <SettingsPage />
          </Protected>
        }
      />
      <Route
        path="/privacy"
        element={
          <Protected>
            <PrivacyPage />
          </Protected>
        }
      />
      <Route
        path="/settings/users"
        element={
          <Protected>
            <SettingsPage />
          </Protected>
        }
      />
      <Route
        path="/forms/:versionId"
        element={
          <Protected>
            <FormPage />
          </Protected>
        }
      />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
