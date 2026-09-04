import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { listDocuments } from "@/api/documents";
import { listTemplates } from "@/api/forms";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";

const STATUSES = ["", "GENERATED", "CONVERTING", "COMPLETED", "FAILED"] as const;

export function DocumentsPage() {
  const { token } = useAuth();
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [templateId, setTemplateId] = useState("");
  const [page, setPage] = useState(0);

  const filters = useMemo(
    () => ({
      q: q.trim() || undefined,
      status: status || undefined,
      templateId: templateId || undefined,
      page,
      size: 10,
    }),
    [q, status, templateId, page],
  );

  const templates = useQuery({
    queryKey: ["templates"],
    queryFn: () => listTemplates(token!),
    enabled: !!token,
  });

  const docs = useQuery({
    queryKey: ["documents", filters],
    queryFn: () => listDocuments(token!, filters),
    enabled: !!token,
  });

  return (
    <div className="mx-auto max-w-5xl px-6 py-10">
      <AppHeader subtitle="Repository des documents generes — recherche et filtres cote serveur." />

      <form
        className="mb-6 grid gap-3 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-4 sm:grid-cols-4"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          void docs.refetch();
        }}
      >
        <label className="block text-sm sm:col-span-2">
          Recherche
          <input
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
            placeholder="Reference ou titre"
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
          />
        </label>
        <label className="block text-sm">
          Statut
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
          >
            {STATUSES.map((s) => (
              <option key={s || "all"} value={s}>
                {s || "Tous"}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm">
          Template
          <select
            value={templateId}
            onChange={(e) => {
              setTemplateId(e.target.value);
              setPage(0);
            }}
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
          >
            <option value="">Tous</option>
            {(templates.data?.items ?? []).map((tpl) => (
              <option key={tpl.id} value={tpl.id}>
                {tpl.name}
              </option>
            ))}
          </select>
        </label>
      </form>

      {docs.isLoading ? <p>Chargement…</p> : null}
      {docs.isError ? <p className="text-[var(--danger)]">Impossible de charger les documents.</p> : null}

      <div className="overflow-x-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b border-[var(--line)] text-[var(--muted)]">
            <tr>
              <th className="px-4 py-3 font-medium">Reference</th>
              <th className="px-4 py-3 font-medium">Titre</th>
              <th className="px-4 py-3 font-medium">Template</th>
              <th className="px-4 py-3 font-medium">Version</th>
              <th className="px-4 py-3 font-medium">Statut</th>
              <th className="px-4 py-3 font-medium">Auteur</th>
              <th className="px-4 py-3 font-medium">Cree le</th>
              <th className="px-4 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {(docs.data?.items ?? []).map((doc) => (
              <tr key={doc.id} className="border-b border-[var(--line)] last:border-0">
                <td className="px-4 py-3 font-mono text-xs">{doc.reference}</td>
                <td className="px-4 py-3">{doc.title}</td>
                <td className="px-4 py-3">
                  <span className="block">{doc.templateName}</span>
                  <span className="text-xs text-[var(--muted)]">{doc.templateCode}</span>
                </td>
                <td className="px-4 py-3">doc v{doc.documentVersionNumber ?? 1}</td>
                <td className="px-4 py-3">{doc.status}</td>
                <td className="px-4 py-3">{doc.createdByName ?? "—"}</td>
                <td className="px-4 py-3 whitespace-nowrap">
                  {new Date(doc.createdAt).toLocaleString("fr-FR")}
                </td>
                <td className="px-4 py-3">
                  <Link to={`/documents/${doc.id}`} className="text-[var(--brand)] underline">
                    Ouvrir
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {!docs.isLoading && (docs.data?.items?.length ?? 0) === 0 ? (
        <p className="mt-6 text-[var(--muted)]">Aucun document pour ces filtres.</p>
      ) : null}

      {docs.data && docs.data.totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-between text-sm">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-xl border border-[var(--line)] px-3 py-1.5 disabled:opacity-40"
          >
            Precedent
          </button>
          <span className="text-[var(--muted)]">
            Page {docs.data.page + 1} / {docs.data.totalPages} ({docs.data.totalElements})
          </span>
          <button
            type="button"
            disabled={page + 1 >= docs.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-xl border border-[var(--line)] px-3 py-1.5 disabled:opacity-40"
          >
            Suivant
          </button>
        </div>
      ) : null}
    </div>
  );
}
