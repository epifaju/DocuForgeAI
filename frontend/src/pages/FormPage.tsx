import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { getFormSchema } from "@/api/forms";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { DynamicForm } from "@/components/DynamicForm";

export function FormPage() {
  const { t } = useTranslation();
  const { versionId = "" } = useParams();
  const { token } = useAuth();
  const query = useQuery({
    queryKey: ["form-schema", versionId],
    queryFn: () => getFormSchema(token!, versionId),
    enabled: !!token && !!versionId,
  });

  return (
    <AppShell
      title={t("form.title")}
      description={t("form.description")}
      width="form"
    >
      {query.isLoading ? <p>{t("form.schemaLoading")}</p> : null}
      {query.isError ? <p className="text-[var(--danger)]">{t("form.schemaMissing")}</p> : null}
      {query.data ? <DynamicForm schema={query.data} /> : null}
    </AppShell>
  );
}
