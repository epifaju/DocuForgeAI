import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";
import { listDocuments } from "@/api/documents";
import { listTemplates } from "@/api/forms";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { StatusBadge } from "@/components/StatusBadge";
import { dateLocale } from "@/i18n";

const STATUSES = ["", "GENERATED", "CONVERTING", "COMPLETED", "FAILED"] as const;

function documentStatusLabel(
  t: (key: string, options?: Record<string, unknown>) => string,
  status: string | null | undefined,
): string {
  if (!status) return "—";
  const key = `documents.statusLabel.${status}`;
  const label = t(key);
  return label === key ? status : label;
}

function parseStatusParam(raw: string | null): string {
  if (!raw) return "";
  return (STATUSES as readonly string[]).includes(raw) ? raw : "";
}

export function DocumentsPage() {
  const { t, i18n } = useTranslation();
  const { token } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [q, setQ] = useState("");
  const status = parseStatusParam(searchParams.get("status"));
  const [templateId, setTemplateId] = useState("");
  const [page, setPage] = useState(0);
  const loc = dateLocale(i18n.language);

  function setStatus(next: string) {
    const params = new URLSearchParams(searchParams);
    if (next) params.set("status", next);
    else params.delete("status");
    setSearchParams(params, { replace: true });
  }

  useEffect(() => {
    setPage(0);
  }, [status]);

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
    <AppShell
      title={t("documents.title")}
      description={t("documents.description")}
      width="wide"
    >
      <form
        className="mb-6 grid gap-3 rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-4 sm:grid-cols-4"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          void docs.refetch();
        }}
      >
        <label className="block text-sm sm:col-span-2">
          {t("documents.search")}
          <input
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
            placeholder={t("documents.searchPlaceholder")}
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
          />
        </label>
        <label className="block text-sm">
          {t("documents.status")}
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
                {s ? documentStatusLabel(t, s) : t("common.all")}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm">
          {t("documents.template")}
          <select
            value={templateId}
            onChange={(e) => {
              setTemplateId(e.target.value);
              setPage(0);
            }}
            className="mt-1 w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2"
          >
            <option value="">{t("common.all")}</option>
            {(templates.data?.items ?? []).map((tpl) => (
              <option key={tpl.id} value={tpl.id}>
                {tpl.name}
              </option>
            ))}
          </select>
        </label>
      </form>

      {docs.isLoading ? <p>{t("common.loading")}</p> : null}
      {docs.isError ? <p className="text-[var(--danger)]">{t("documents.loadError")}</p> : null}

      <div className="overflow-x-auto rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b border-[var(--line)] text-[var(--muted)]">
            <tr>
              <th className="px-4 py-3 font-medium">{t("documents.colReference")}</th>
              <th className="px-4 py-3 font-medium">{t("documents.colTitle")}</th>
              <th className="px-4 py-3 font-medium">{t("documents.colTemplate")}</th>
              <th className="px-4 py-3 font-medium">{t("documents.colVersion")}</th>
              <th className="px-4 py-3 font-medium">{t("documents.colStatus")}</th>
              <th className="px-4 py-3 font-medium">{t("documents.colAuthor")}</th>
              <th className="px-4 py-3 font-medium">{t("documents.colCreated")}</th>
              <th className="px-4 py-3 font-medium">{t("documents.colActions")}</th>
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
                <td className="px-4 py-3">
                  {t("documents.docVersion", { n: doc.documentVersionNumber ?? 1 })}
                </td>
                <td className="px-4 py-3">
                  {doc.status ? (
                    <StatusBadge status={doc.status} label={documentStatusLabel(t, doc.status)} />
                  ) : (
                    "—"
                  )}
                </td>
                <td className="px-4 py-3">{doc.createdByName ?? "—"}</td>
                <td className="px-4 py-3 whitespace-nowrap">
                  {new Date(doc.createdAt).toLocaleString(loc)}
                </td>
                <td className="px-4 py-3">
                  <Link to={`/documents/${doc.id}`} className="text-[var(--brand)] underline">
                    {t("common.open")}
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {!docs.isLoading && (docs.data?.items?.length ?? 0) === 0 ? (
        <p className="mt-6 text-[var(--muted)]">{t("documents.empty")}</p>
      ) : null}

      {docs.data && docs.data.totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-between text-sm">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-xl border border-[var(--line)] px-3 py-1.5 disabled:opacity-40"
          >
            {t("common.previous")}
          </button>
          <span className="text-[var(--muted)]">
            {t("common.pageOf", {
              current: docs.data.page + 1,
              total: docs.data.totalPages,
              count: docs.data.totalElements,
            })}
          </span>
          <button
            type="button"
            disabled={page + 1 >= docs.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-xl border border-[var(--line)] px-3 py-1.5 disabled:opacity-40"
          >
            {t("common.next")}
          </button>
        </div>
      ) : null}
    </AppShell>
  );
}
