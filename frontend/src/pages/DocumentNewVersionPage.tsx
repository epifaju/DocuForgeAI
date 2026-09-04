import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { getDocument } from "@/api/documents";
import { getFormSchema } from "@/api/forms";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";
import { DynamicForm } from "@/components/DynamicForm";

export function DocumentNewVersionPage() {
  const { id = "" } = useParams();
  const { token } = useAuth();

  const docQuery = useQuery({
    queryKey: ["document", id],
    queryFn: () => getDocument(token!, id),
    enabled: !!token && !!id,
  });

  const schemaQuery = useQuery({
    queryKey: ["form-schema", docQuery.data?.templateVersionId],
    queryFn: () => getFormSchema(token!, docQuery.data!.templateVersionId),
    enabled: !!token && !!docQuery.data?.templateVersionId,
  });

  return (
    <div className="mx-auto max-w-4xl px-6 py-10">
      <AppHeader subtitle="Nouvelle version — le document d'origine reste inchange." />
      <p className="mb-4 text-sm">
        <Link to={`/documents/${id}`} className="text-[var(--brand)] underline">
          ← Retour au document
        </Link>
      </p>

      {docQuery.isLoading || schemaQuery.isLoading ? <p>Chargement…</p> : null}
      {docQuery.isError || schemaQuery.isError ? (
        <p className="text-[var(--danger)]">Impossible de preparer la nouvelle version.</p>
      ) : null}

      {docQuery.data && schemaQuery.data ? (
        <DynamicForm
          schema={schemaQuery.data}
          initialValues={docQuery.data.data ?? undefined}
          mode="new-version"
          sourceDocumentId={docQuery.data.id}
          submitLabel="Creer la version"
        />
      ) : null}
    </div>
  );
}
