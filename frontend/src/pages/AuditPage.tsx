import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { listAudit } from "@/api/audit";
import { useAuth } from "@/auth/AuthContext";
import { AppHeader } from "@/components/AppHeader";

const ACTIONS = [
  "",
  "LOGIN",
  "TEMPLATE_CREATED",
  "TEMPLATE_UPDATED",
  "TEMPLATE_ACTIVATED",
  "TEMPLATE_ARCHIVED",
  "DOCUMENT_GENERATED",
  "DOCUMENT_DOWNLOADED",
  "DOCUMENT_EMAILED",
  "DOCUMENT_VERSION_CREATED",
  "AI_REQUEST",
  "BATCH_STARTED",
  "BATCH_COMPLETED",
  "SETTINGS_CHANGED",
] as const;

export function AuditPage() {
  const { token } = useAuth();
  const [action, setAction] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);

  const filters = useMemo(
    () => ({
      action: action || undefined,
      status: status || undefined,
      page,
      size: 20,
    }),
    [action, status, page],
  );

  const audit = useQuery({
    queryKey: ["audit", filters],
    queryFn: () => listAudit(token!, filters),
    enabled: !!token,
  });

  return (
    <div className="mx-auto max-w-5xl px-6 py-10">
      <AppHeader subtitle="Journal d'audit — operations sensibles de la societe." />

      <form
        className="mb-6 grid gap-3 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-4 sm:grid-cols-3"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          void audit.refetch();
        }}
      >
        <label className="block text-sm">
          Action
          <select
            value={action}
            onChange={(e) => {
              setAction(e.target.value);
              setPage(0);
            }}
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
          >
            {ACTIONS.map((a) => (
              <option key={a || "all"} value={a}>
                {a || "Toutes"}
              </option>
            ))}
          </select>
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
            <option value="">Tous</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILURE">FAILURE</option>
            <option value="FAILED">FAILED</option>
          </select>
        </label>
        <div className="flex items-end">
          <button
            type="submit"
            className="rounded-xl bg-[var(--brand)] px-4 py-2 text-sm font-medium text-white"
          >
            Filtrer
          </button>
        </div>
      </form>

      {audit.isLoading ? <p>Chargement…</p> : null}
      {audit.isError ? (
        <p className="text-[var(--danger)]">
          Acces audit refuse ou indisponible (roles ADMIN / EDITOR).
        </p>
      ) : null}

      {audit.data ? (
        <>
          <p className="mb-3 text-sm text-[var(--muted)]">
            {audit.data.totalElements} evenement(s)
          </p>
          <div className="overflow-x-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-[var(--line)] text-[var(--muted)]">
                <tr>
                  <th className="px-4 py-3 font-medium">Date</th>
                  <th className="px-4 py-3 font-medium">Action</th>
                  <th className="px-4 py-3 font-medium">Entite</th>
                  <th className="px-4 py-3 font-medium">Statut</th>
                  <th className="px-4 py-3 font-medium">Utilisateur</th>
                </tr>
              </thead>
              <tbody>
                {audit.data.items.map((row) => (
                  <tr key={row.id} className="border-b border-[var(--line)] last:border-0">
                    <td className="px-4 py-3 whitespace-nowrap">
                      {new Date(row.createdAt).toLocaleString("fr-FR")}
                    </td>
                    <td className="px-4 py-3 font-mono text-xs">{row.action}</td>
                    <td className="px-4 py-3">
                      {row.entityType ?? "—"}
                      {row.entityId ? (
                        <span className="ml-1 font-mono text-xs text-[var(--muted)]">
                          {row.entityId.slice(0, 8)}…
                        </span>
                      ) : null}
                    </td>
                    <td className="px-4 py-3">{row.status}</td>
                    <td className="px-4 py-3">{row.userEmail ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex gap-2">
            <button
              type="button"
              disabled={page <= 0}
              className="rounded-xl border border-[var(--line)] px-3 py-1.5 text-sm disabled:opacity-40"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Precedent
            </button>
            <button
              type="button"
              disabled={page + 1 >= audit.data.totalPages}
              className="rounded-xl border border-[var(--line)] px-3 py-1.5 text-sm disabled:opacity-40"
              onClick={() => setPage((p) => p + 1)}
            >
              Suivant
            </button>
          </div>
        </>
      ) : null}
    </div>
  );
}
