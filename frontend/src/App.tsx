import { Navigate, Route, Routes } from "react-router-dom";
import type { ReactNode } from "react";
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

function Protected({ children }: { children: ReactNode }) {
  const { token, ready } = useAuth();
  if (!ready) return null;
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
