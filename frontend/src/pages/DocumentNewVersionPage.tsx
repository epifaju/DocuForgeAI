import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { getDocument } from "@/api/documents";
import { getFormSchema } from "@/api/forms";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { DynamicForm } from "@/components/DynamicForm";

export function DocumentNewVersionPage() {
  const { t } = useTranslation();
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
    <AppShell
      title={t("newVersion.title")}
      description={t("newVersion.description")}
      width="form"
      actions={
        <Link to={`/documents/${id}`} className="text-sm text-[var(--brand)] underline">
          {t("newVersion.back")}
        </Link>
      }
    >
      {docQuery.isLoading || schemaQuery.isLoading ? <p>{t("common.loading")}</p> : null}
      {docQuery.isError || schemaQuery.isError ? (
        <p className="text-[var(--danger)]">{t("newVersion.prepareError")}</p>
      ) : null}

      {docQuery.data && schemaQuery.data ? (
        <DynamicForm
          schema={schemaQuery.data}
          initialValues={docQuery.data.data ?? undefined}
          mode="new-version"
          sourceDocumentId={docQuery.data.id}
          submitLabel={t("newVersion.submit")}
        />
      ) : null}
    </AppShell>
  );
}
