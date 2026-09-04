import { Navigate, Route, Routes } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuth } from "@/auth/AuthContext";
import { AuditPage } from "@/pages/AuditPage";
import { BatchDetailPage } from "@/pages/BatchDetailPage";
import { BatchesPage } from "@/pages/BatchesPage";
import { DashboardPage } from "@/pages/DashboardPage";
import { DocumentDetailPage } from "@/pages/DocumentDetailPage";
import { DocumentNewVersionPage } from "@/pages/DocumentNewVersionPage";
import { DocumentsPage } from "@/pages/DocumentsPage";
import { FormPage } from "@/pages/FormPage";
import { LoginPage } from "@/pages/LoginPage";
import { TemplatesPage } from "@/pages/TemplatesPage";
import { UsersPage } from "@/pages/UsersPage";

function Protected({ children }: { children: ReactNode }) {
  const { token } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
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
