import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { getFormSchema } from "@/api/forms";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";
import { DynamicForm } from "@/components/DynamicForm";

export function FormPage() {
  const { versionId = "" } = useParams();
  const { token } = useAuth();
  const query = useQuery({
    queryKey: ["form-schema", versionId],
    queryFn: () => getFormSchema(token!, versionId),
    enabled: !!token && !!versionId,
  });

  return (
    <div className="mx-auto max-w-4xl px-6 py-10">
      <AppHeader subtitle="Remplissez le formulaire dynamique pour generer un document." />
      <div className="mt-2">
        {query.isLoading ? <p>Chargement du schema…</p> : null}
        {query.isError ? <p className="text-[var(--danger)]">Schema introuvable.</p> : null}
        {query.data ? <DynamicForm schema={query.data} /> : null}
      </div>
    </div>
  );
}
