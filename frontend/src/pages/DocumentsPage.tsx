import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Eye, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";
import { listDocuments } from "@/api/documents";
import { listTemplates } from "@/api/forms";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { CardList, CardListItem } from "@/components/CardList";
import { StatusBadge } from "@/components/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { dateLocale } from "@/i18n";
import { cn } from "@/lib/cn";
import { formatRelative } from "@/lib/formatRelative";

const STATUSES = ["", "GENERATED", "CONVERTING", "COMPLETED", "FAILED"] as const;

const pillClass =
  "inline-flex min-h-9 items-center gap-1.5 rounded-full border border-[var(--line)] bg-[var(--surface)] px-3 py-1.5 text-xs font-medium text-[var(--brand-ink)] transition-colors hover:border-[var(--brand)]/40 hover:bg-[var(--brand-soft)]";

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

  const items = docs.data?.items ?? [];
  const totalElements = docs.data?.totalElements ?? 0;

  return (
    <AppShell
      title={t("documents.title")}
      description={t("documents.description")}
      width="wide"
    >
      <Card padding="sm" className="mb-6">
        <form
          className="grid gap-3 md:grid-cols-3"
          onSubmit={(e) => {
            e.preventDefault();
            setPage(0);
            void docs.refetch();
          }}
        >
          <Field label={t("documents.search")}>
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--muted)]"
                aria-hidden
              />
              <Input
                value={q}
                onChange={(e) => {
                  setQ(e.target.value);
                  setPage(0);
                }}
                placeholder={t("documents.searchPlaceholder")}
                className="pl-9"
              />
            </div>
          </Field>
          <Field label={t("documents.status")}>
            <Select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              {STATUSES.map((s) => (
                <option key={s || "all"} value={s}>
                  {s ? documentStatusLabel(t, s) : t("common.all")}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("documents.template")}>
            <Select
              value={templateId}
              onChange={(e) => {
                setTemplateId(e.target.value);
                setPage(0);
              }}
            >
              <option value="">{t("common.all")}</option>
              {(templates.data?.items ?? []).map((tpl) => (
                <option key={tpl.id} value={tpl.id}>
                  {tpl.name}
                </option>
              ))}
            </Select>
          </Field>
        </form>
      </Card>

      {docs.isLoading ? <p>{t("common.loading")}</p> : null}
      {docs.isError ? <p className="text-[var(--danger)]">{t("documents.loadError")}</p> : null}

      <CardList>
        {items.map((doc) => {
          const failed = doc.status === "FAILED";
          return (
            <CardListItem
              key={doc.id}
              className={cn(failed && "border-[var(--danger)]/25 bg-[var(--danger-soft)]/40")}
            >
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-mono text-xs text-[var(--brand)]">{doc.reference}</p>
                  <p className="mt-1 font-medium text-[var(--brand-ink)]">{doc.title}</p>
                </div>
                {doc.status ? (
                  <StatusBadge status={doc.status} label={documentStatusLabel(t, doc.status)} />
                ) : null}
              </div>
              <dl className="mt-3 grid gap-1.5 text-sm">
                <div className="flex justify-between gap-3">
                  <dt className="text-[var(--muted)]">{t("documents.colTemplate")}</dt>
                  <dd className="min-w-0 text-right text-[var(--ink)]">
                    <span className="block truncate">{doc.templateName}</span>
                    <span className="text-xs text-[var(--muted)]">{doc.templateCode}</span>
                  </dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-[var(--muted)]">{t("documents.colVersion")}</dt>
                  <dd className="text-[var(--muted)]">
                    {t("documents.docVersion", { n: doc.documentVersionNumber ?? 1 })}
                  </dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-[var(--muted)]">{t("documents.colAuthor")}</dt>
                  <dd className="text-[var(--muted)]">{doc.createdByName ?? "—"}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-[var(--muted)]">{t("documents.colCreated")}</dt>
                  <dd className="text-[var(--muted)]">
                    <time dateTime={doc.createdAt} title={new Date(doc.createdAt).toLocaleString(loc)}>
                      {formatRelative(doc.createdAt, loc)}
                    </time>
                  </dd>
                </div>
              </dl>
              <div className="mt-3 border-t border-[var(--line)] pt-2">
                <Link to={`/documents/${doc.id}`} className={pillClass}>
                  <Eye className="h-3.5 w-3.5" aria-hidden />
                  {t("common.open")}
                </Link>
              </div>
            </CardListItem>
          );
        })}
      </CardList>

      <div className="hidden overflow-x-auto rounded-[var(--radius-xl)] border border-[var(--line)] bg-[var(--surface)] md:block">
        <table className="min-w-[56rem] w-full text-left text-sm xl:min-w-0">
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
            {items.map((doc) => {
              const failed = doc.status === "FAILED";
              return (
                <tr
                  key={doc.id}
                  className={cn(
                    "border-b border-[var(--line)] last:border-0",
                    failed && "bg-[var(--danger-soft)]/50",
                  )}
                >
                  <td className="px-4 py-3 font-mono text-xs text-[var(--brand)]">{doc.reference}</td>
                  <td className="px-4 py-3 font-medium text-[var(--brand-ink)]">{doc.title}</td>
                  <td className="px-4 py-3">
                    <span className="block">{doc.templateName}</span>
                    <span className="text-xs text-[var(--muted)]">{doc.templateCode}</span>
                  </td>
                  <td className="px-4 py-3 text-[var(--muted)]">
                    {t("documents.docVersion", { n: doc.documentVersionNumber ?? 1 })}
                  </td>
                  <td className="px-4 py-3">
                    {doc.status ? (
                      <StatusBadge status={doc.status} label={documentStatusLabel(t, doc.status)} />
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="px-4 py-3 text-[var(--muted)]">{doc.createdByName ?? "—"}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-[var(--muted)]">
                    <time dateTime={doc.createdAt} title={new Date(doc.createdAt).toLocaleString(loc)}>
                      {formatRelative(doc.createdAt, loc)}
                    </time>
                  </td>
                  <td className="px-4 py-3">
                    <Link to={`/documents/${doc.id}`} className={pillClass}>
                      <Eye className="h-3.5 w-3.5" aria-hidden />
                      {t("common.open")}
                    </Link>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {!docs.isLoading && items.length === 0 ? (
        <p className="mt-6 text-[var(--muted)]">{t("documents.empty")}</p>
      ) : null}

      {docs.data && items.length > 0 ? (
        <p className="mt-3 text-sm text-[var(--muted)]">
          {t("documents.countShown", { shown: items.length, total: totalElements })}
          <span className="xl:hidden">
            {" · "}
            {t("documents.scrollHint")}
          </span>
        </p>
      ) : null}

      {docs.data && docs.data.totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-between text-sm">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            {t("common.previous")}
          </Button>
          <span className="text-[var(--muted)]">
            {t("common.pageOf", {
              current: docs.data.page + 1,
              total: docs.data.totalPages,
              count: docs.data.totalElements,
            })}
          </span>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={page + 1 >= docs.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            {t("common.next")}
          </Button>
        </div>
      ) : null}
    </AppShell>
  );
}
