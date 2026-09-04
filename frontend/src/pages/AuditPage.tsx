import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { listAudit } from "@/api/audit";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { dateLocale } from "@/i18n";

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
  const { t, i18n } = useTranslation();
  const { token } = useAuth();
  const loc = dateLocale(i18n.language);
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
    <AppShell
      title={t("audit.title")}
      description={t("audit.description")}
      width="wide"
    >
      <form
        className="mb-6 grid gap-3 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-4 sm:grid-cols-3"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          void audit.refetch();
        }}
      >
        <label className="block text-sm">
          {t("audit.action")}
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
                {a || t("common.allFeminine")}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm">
          {t("audit.status")}
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
          >
            <option value="">{t("common.all")}</option>
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
            {t("audit.filter")}
          </button>
        </div>
      </form>

      {audit.isLoading ? <p>{t("common.loading")}</p> : null}
      {audit.isError ? (
        <p className="text-[var(--danger)]">{t("audit.denied")}</p>
      ) : null}

      {audit.data ? (
        <>
          <p className="mb-3 text-sm text-[var(--muted)]">
            {t("audit.events", { count: audit.data.totalElements })}
          </p>
          <div className="overflow-x-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-[var(--line)] text-[var(--muted)]">
                <tr>
                  <th className="px-4 py-3 font-medium">{t("audit.colDate")}</th>
                  <th className="px-4 py-3 font-medium">{t("audit.colAction")}</th>
                  <th className="px-4 py-3 font-medium">{t("audit.colEntity")}</th>
                  <th className="px-4 py-3 font-medium">{t("audit.colStatus")}</th>
                  <th className="px-4 py-3 font-medium">{t("audit.colUser")}</th>
                </tr>
              </thead>
              <tbody>
                {audit.data.items.map((row) => (
                  <tr key={row.id} className="border-b border-[var(--line)] last:border-0">
                    <td className="px-4 py-3 whitespace-nowrap">
                      {new Date(row.createdAt).toLocaleString(loc)}
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
              {t("common.previous")}
            </button>
            <button
              type="button"
              disabled={page + 1 >= audit.data.totalPages}
              className="rounded-xl border border-[var(--line)] px-3 py-1.5 text-sm disabled:opacity-40"
              onClick={() => setPage((p) => p + 1)}
            >
              {t("common.next")}
            </button>
          </div>
        </>
      ) : null}
    </AppShell>
  );
}
