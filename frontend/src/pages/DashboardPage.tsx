import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { fetchDashboard } from "@/api/dashboard";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/AppShell";
import { StatusBadge } from "@/components/StatusBadge";
import { dateLocale } from "@/i18n";

export function DashboardPage() {
  const { token } = useAuth();
  const { t, i18n } = useTranslation();
  const dash = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => fetchDashboard(token!),
    enabled: !!token,
    refetchInterval: 30_000,
  });

  const kpis = dash.data?.kpis;
  const loc = dateLocale(i18n.language);
  const docs = dash.data?.recentDocuments ?? [];
  const activity = dash.data?.recentActivity ?? [];
  const hasFailures = (kpis?.failedGenerations ?? 0) > 0;
  const hasActiveBatches = (kpis?.batchJobsActive ?? 0) > 0;

  return (
    <AppShell
      title={t("dashboard.title")}
      description={t("dashboard.description")}
      width="wide"
      actions={
        <>
          <Link
            to="/templates"
            className="rounded-xl border border-[var(--line)] bg-[var(--surface)] px-3.5 py-2 text-sm text-[var(--brand-ink)] transition-colors hover:border-[var(--brand)] hover:bg-[var(--bg-accent)]"
          >
            {t("dashboard.actionTemplates")}
          </Link>
          <Link
            to="/documents"
            className="rounded-xl bg-[var(--brand)] px-3.5 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--brand-ink)]"
          >
            {t("dashboard.actionDocuments")}
          </Link>
        </>
      }
    >
      {dash.isLoading ? <DashboardSkeleton /> : null}
      {dash.isError ? <p className="text-[var(--danger)]">{t("dashboard.unavailable")}</p> : null}

      {kpis ? (
        <div className="space-y-8">
          <section
            className="dash-rise overflow-hidden rounded-2xl border border-[var(--line)] bg-[var(--surface)]"
            style={{ animationDelay: "40ms" }}
          >
            <div className="dash-overview-wash relative grid lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1.4fr)]">
              <div className="relative border-b border-[var(--line)] p-6 sm:p-8 lg:border-b-0 lg:border-r">
                <div className="flex items-center gap-2">
                  <span
                    className={`h-1.5 w-1.5 rounded-full ${
                      hasFailures ? "bg-[var(--danger)]" : "bg-[var(--brand)]"
                    }`}
                    aria-hidden
                  />
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[var(--muted)]">
                    {t("dashboard.docsToday")}
                  </p>
                </div>
                <p
                  className={`dash-count brand mt-3 text-6xl leading-none tracking-tight sm:text-7xl ${
                    kpis.documentsGeneratedToday > 0
                      ? "text-[var(--brand-ink)]"
                      : "text-[var(--muted)]"
                  }`}
                  style={{ animationDelay: "100ms" }}
                >
                  {kpis.documentsGeneratedToday}
                </p>
                <p className="mt-3 max-w-xs text-sm text-[var(--muted)]">
                  {t("dashboard.docsTodayHint")}
                </p>
                <Link
                  to="/documents"
                  className="mt-6 inline-flex items-center gap-2 text-sm font-medium text-[var(--brand)] transition-colors hover:text-[var(--brand-ink)]"
                >
                  {t("dashboard.openDocuments")}
                  <span
                    className="inline-flex h-5 w-5 items-center justify-center rounded-md bg-[var(--bg-accent)] text-xs"
                    aria-hidden
                  >
                    →
                  </span>
                </Link>
              </div>

              <div className="grid sm:grid-cols-3">
                <KpiCell
                  label={t("dashboard.docsMonth")}
                  value={kpis.documentsGeneratedThisMonth}
                  hint={t("dashboard.docsMonthHint")}
                  to="/documents"
                  delay="140ms"
                />
                <KpiCell
                  label={t("dashboard.activeTemplates")}
                  value={kpis.activeTemplates}
                  hint={t("dashboard.activeTemplatesHint")}
                  to="/templates"
                  delay="200ms"
                  className="sm:border-l"
                />
                <KpiCell
                  label={t("dashboard.failures")}
                  value={kpis.failedGenerations}
                  hint={t("dashboard.failuresHint")}
                  to="/documents?status=FAILED"
                  emphasis={hasFailures}
                  delay="260ms"
                  className="border-t sm:border-l sm:border-t-0"
                />
              </div>
            </div>
          </section>

          <section
            className="dash-rise flex flex-wrap items-center gap-x-1 gap-y-2"
            style={{ animationDelay: "140ms" }}
          >
            <OpsChip
              label={t("dashboard.batchesTotal")}
              value={kpis.batchJobsTotal}
              to="/batches"
            />
            <OpsChip
              label={t("dashboard.batchesActive")}
              value={kpis.batchJobsActive}
              to="/batches"
              live={hasActiveBatches}
            />
            <OpsChip label={t("dashboard.aiToday")} value={kpis.aiRequestsToday} />
            <OpsChip label={t("dashboard.aiMonth")} value={kpis.aiRequestsThisMonth} />
            <span className="ml-auto flex items-center gap-2 text-xs text-[var(--muted)]">
              <span className="hidden h-1 w-1 rounded-full bg-[var(--brand)] sm:inline-block" aria-hidden />
              {t("dashboard.timezone", { tz: dash.data?.timezone })}
            </span>
          </section>

          <section
            className="dash-rise grid gap-5 lg:grid-cols-2"
            style={{ animationDelay: "200ms" }}
          >
            <FeedPanel
              title={t("dashboard.recentDocs")}
              linkTo="/documents"
              linkLabel={t("dashboard.seeAll")}
              empty={
                <div className="flex flex-col items-start gap-3 py-2">
                  <p className="text-sm text-[var(--muted)]">{t("dashboard.noDocs")}</p>
                  <Link
                    to="/templates"
                    className="rounded-lg border border-[var(--line)] px-3 py-1.5 text-sm font-medium text-[var(--brand-ink)] transition-colors hover:border-[var(--brand)] hover:bg-[var(--bg-accent)]"
                  >
                    {t("dashboard.emptyDocsCta")}
                  </Link>
                </div>
              }
              isEmpty={docs.length === 0}
            >
              {docs.map((doc, i) => (
                <li key={doc.id} className="dash-rise" style={{ animationDelay: `${240 + i * 40}ms` }}>
                  <Link
                    to={`/documents/${doc.id}`}
                    className="group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 px-4 py-3.5 transition-colors hover:bg-[var(--bg-accent)]/55 sm:px-5"
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                        <span className="font-mono text-xs font-medium text-[var(--brand)] transition-colors group-hover:text-[var(--brand-ink)]">
                          {doc.reference}
                        </span>
                        <span className="truncate text-sm text-[var(--brand-ink)]">{doc.title}</span>
                      </div>
                      {doc.templateName ? (
                        <p className="mt-0.5 truncate text-xs text-[var(--muted)]">{doc.templateName}</p>
                      ) : null}
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-1.5">
                      <StatusBadge
                        status={doc.status}
                        label={t(`documents.statusLabel.${doc.status}`, { defaultValue: doc.status })}
                      />
                      <time
                        className="text-[11px] tabular-nums text-[var(--muted)]"
                        dateTime={doc.createdAt}
                        title={new Date(doc.createdAt).toLocaleString(loc)}
                      >
                        {formatRelative(doc.createdAt, loc)}
                      </time>
                    </div>
                  </Link>
                </li>
              ))}
            </FeedPanel>

            <FeedPanel
              title={t("dashboard.recentActivity")}
              linkTo="/audit"
              linkLabel={t("dashboard.auditLink")}
              empty={<p className="py-2 text-sm text-[var(--muted)]">{t("dashboard.noActivity")}</p>}
              isEmpty={activity.length === 0}
            >
              {activity.map((row, i) => (
                <li
                  key={row.id}
                  className="dash-rise grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 px-4 py-3.5 sm:px-5"
                  style={{ animationDelay: `${260 + i * 40}ms` }}
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-[var(--brand-ink)]">
                      {t(`audit.actionLabel.${row.action}`, { defaultValue: row.action })}
                    </p>
                    <p className="mt-0.5 truncate text-xs text-[var(--muted)]">
                      {row.userEmail ?? "—"}
                    </p>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1.5">
                    <StatusBadge
                      status={row.status}
                      label={t(`audit.statusLabel.${row.status}`, { defaultValue: row.status })}
                    />
                    <time
                      className="text-[11px] tabular-nums text-[var(--muted)]"
                      dateTime={row.createdAt}
                      title={new Date(row.createdAt).toLocaleString(loc)}
                    >
                      {formatRelative(row.createdAt, loc)}
                    </time>
                  </div>
                </li>
              ))}
            </FeedPanel>
          </section>
        </div>
      ) : null}
    </AppShell>
  );
}

function KpiCell({
  label,
  value,
  hint,
  to,
  emphasis,
  delay,
  className = "",
}: {
  label: string;
  value: number;
  hint: string;
  to?: string;
  emphasis?: boolean;
  delay?: string;
  className?: string;
}) {
  const body = (
    <>
      <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-[var(--muted)]">
        {label}
      </p>
      <p
        className={`dash-count mt-3 text-3xl font-semibold tabular-nums tracking-tight sm:text-4xl ${
          emphasis ? "text-[var(--danger)]" : "text-[var(--brand-ink)]"
        }`}
        style={{ animationDelay: delay }}
      >
        {value}
      </p>
      <p className="mt-1.5 text-xs text-[var(--muted)]">{hint}</p>
    </>
  );

  const shell =
    `block h-full border-[var(--line)] p-5 sm:p-6 outline-none transition-colors hover:bg-[var(--bg-accent)]/45 focus-visible:bg-[var(--bg-accent)]/45 ${className}`;

  if (to) {
    return (
      <Link to={to} className={shell}>
        {body}
      </Link>
    );
  }
  return <div className={shell}>{body}</div>;
}

function OpsChip({
  label,
  value,
  to,
  live,
}: {
  label: string;
  value: number;
  to?: string;
  live?: boolean;
}) {
  const content = (
    <span className="inline-flex items-center gap-2 rounded-lg border border-[var(--line)] bg-[var(--surface)] px-3 py-1.5 text-sm transition-colors">
      {live ? (
        <span className="relative flex h-1.5 w-1.5">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[var(--brand)] opacity-40" />
          <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-[var(--brand)]" />
        </span>
      ) : null}
      <span className="text-[var(--muted)]">{label}</span>
      <span className="font-semibold tabular-nums text-[var(--brand-ink)]">{value}</span>
    </span>
  );

  if (to) {
    return (
      <Link to={to} className="hover:[&>span]:border-[var(--brand)]">
        {content}
      </Link>
    );
  }
  return content;
}

function FeedPanel({
  title,
  linkTo,
  linkLabel,
  children,
  empty,
  isEmpty,
}: {
  title: string;
  linkTo: string;
  linkLabel: string;
  children: ReactNode;
  empty: ReactNode;
  isEmpty: boolean;
}) {
  return (
    <div className="overflow-hidden rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
      <div className="flex items-center justify-between gap-3 border-b border-[var(--line)] px-4 py-3.5 sm:px-5">
        <h2 className="text-[15px] font-semibold tracking-tight text-[var(--brand-ink)]">{title}</h2>
        <Link
          to={linkTo}
          className="text-xs font-medium text-[var(--brand)] transition-colors hover:text-[var(--brand-ink)]"
        >
          {linkLabel}
        </Link>
      </div>
      {isEmpty ? <div className="px-4 py-8 sm:px-5">{empty}</div> : <ul className="divide-y divide-[var(--line)]/70">{children}</ul>}
    </div>
  );
}

function formatRelative(iso: string, locale: string) {
  const date = new Date(iso);
  const diffSec = Math.round((date.getTime() - Date.now()) / 1000);
  const abs = Math.abs(diffSec);
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });
  if (abs < 60) return rtf.format(diffSec, "second");
  if (abs < 3600) return rtf.format(Math.round(diffSec / 60), "minute");
  if (abs < 86400) return rtf.format(Math.round(diffSec / 3600), "hour");
  if (abs < 86400 * 30) return rtf.format(Math.round(diffSec / 86400), "day");
  return date.toLocaleDateString(locale, { day: "numeric", month: "short" });
}

function DashboardSkeleton() {
  return (
    <div className="animate-pulse space-y-8" aria-hidden>
      <div className="overflow-hidden rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
        <div className="grid lg:grid-cols-2">
          <div className="border-b border-[var(--line)] p-8 lg:border-b-0 lg:border-r">
            <div className="h-3 w-32 rounded bg-[var(--line)]" />
            <div className="mt-4 h-16 w-24 rounded bg-[var(--line)]" />
            <div className="mt-4 h-3 w-40 rounded bg-[var(--line)]" />
          </div>
          <div className="grid sm:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="border-[var(--line)] p-6 sm:border-l">
                <div className="h-2.5 w-16 rounded bg-[var(--line)]" />
                <div className="mt-4 h-9 w-12 rounded bg-[var(--line)]" />
              </div>
            ))}
          </div>
        </div>
      </div>
      <div className="flex gap-2">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-8 w-28 rounded-lg bg-[var(--line)]/60" />
        ))}
      </div>
      <div className="grid gap-5 lg:grid-cols-2">
        {[0, 1].map((col) => (
          <div key={col} className="overflow-hidden rounded-2xl border border-[var(--line)] bg-[var(--surface)]">
            <div className="border-b border-[var(--line)] px-5 py-4">
              <div className="h-3.5 w-36 rounded bg-[var(--line)]" />
            </div>
            {[0, 1, 2, 3].map((row) => (
              <div key={row} className="border-b border-[var(--line)]/50 px-5 py-4 last:border-0">
                <div className="h-3 w-3/4 rounded bg-[var(--line)]/70" />
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
